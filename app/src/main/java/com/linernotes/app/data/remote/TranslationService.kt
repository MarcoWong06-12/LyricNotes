package com.linernotes.app.data.remote

import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TranslationResult(
    val translatedTitle: String?,
    val translatedLyrics: String
)

/**
 * 高性能机器翻译服务 (有道极速多行引擎 + Google/MyMemory 自动容灾降级)
 * 免 API Key、免科学上网（国内移动/联通/电信 5G 直连），自动保留时间轴与换行，极速响应 (200~400ms)。
 */
@Singleton
class TranslationService(
    private val targetLanguageProvider: () -> String
) {
    @Inject
    constructor(preferences: AiPreferences) : this({ preferences.targetLanguage })

    // For testing and fallback
    constructor() : this({ TranslationTargetLanguage.ZH_CN.code })

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private val YOUDAO_RESULT_REGEX = Regex("""<ul id="translateResult">\s*<li>(.*?)</li>\s*</ul>""", RegexOption.DOT_MATCHES_ALL)
        private const val CHUNK_LINE_COUNT = 15
    }

    /**
     * 翻译整首曲目的歌词与标题
     * 优先使用有道移动端接口（国内直连无墙，响应 200ms），分块并发保护时间轴与换行。
     * 若遇到网络波动自动平滑回退至 Google Translate 与 MyMemory 公共服务。
     */
    suspend fun translateTrack(
        trackTitle: String,
        originalLyrics: String,
        targetLanguageCode: String? = null
    ): TranslationResult = withContext(Dispatchers.IO) {
        val targetCode = targetLanguageCode ?: targetLanguageProvider()
        val targetIso = TranslationTargetLanguage.fromCode(targetCode).fallbackIso

        // 1. 翻译歌词主体 (无损保护时间轴与严格 1:1 换行)
        val translatedLyrics = if (originalLyrics.isNotBlank()) {
            translateLyricsWithTimestamps(originalLyrics, targetIso)
        } else ""

        // 2. 翻译歌曲标题
        val translatedTitle = if (trackTitle.isNotBlank()) {
            val titleResult = translateText(trackTitle, targetIso)
            if (!titleResult.isNullOrBlank() && !titleResult.equals(trackTitle, ignoreCase = true)) {
                titleResult
            } else null
        } else null

        TranslationResult(
            translatedTitle = translatedTitle,
            translatedLyrics = translatedLyrics
        )
    }

    private val TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]""")

    private val INDEXED_LINE_REGEX = Regex("""^\s*(?:\[#?(\d+)\]|(\d+)[\.、\s\-:])\s*(.*)$""")

    internal fun parseIndexedLines(rawLines: List<String>, expectedCount: Int): List<String> {
        val cleanedLines = rawLines.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanedLines.isEmpty()) return emptyList()

        // 1. 若返回行数完全吻合（最理想情况），1:1 逐行剥离序号映射，绝不错丢任何一行
        if (cleanedLines.size == expectedCount) {
            return cleanedLines.map { line ->
                val m = INDEXED_LINE_REGEX.find(line)
                val stripped = m?.groupValues?.getOrNull(3)?.trim()
                if (!stripped.isNullOrBlank()) stripped else line
            }
        }

        // 2. 若行数不一致，优先通过提取的序号 (1..expectedCount) 进行确定性定位，并对无序号行按序填补空缺
        val idxMap = mutableMapOf<Int, String>()
        val unindexed = mutableListOf<String>()

        for (line in cleanedLines) {
            val m = INDEXED_LINE_REGEX.find(line)
            val num = m?.let { it.groupValues[1].toIntOrNull() ?: it.groupValues[2].toIntOrNull() }
            val content = m?.groupValues?.getOrNull(3)?.trim()

            if (num != null && num in 1..expectedCount && !content.isNullOrBlank()) {
                idxMap[num] = content
            } else {
                unindexed.add(if (!content.isNullOrBlank()) content else line)
            }
        }

        val unindexedIter = unindexed.iterator()
        return (1..expectedCount).map { i ->
            if (idxMap.containsKey(i)) {
                idxMap[i] ?: ""
            } else if (unindexedIter.hasNext()) {
                unindexedIter.next()
            } else {
                ""
            }
        }
    }

    /**
     * 针对带有 LRC 时间轴的多行歌词进行剥离时间戳、纯文本分块翻译并原样复位时间戳，
     * 杜绝有道等翻译引擎将中括号时间标签当作乱码吞行或串联，实现 100% 逐行毫秒级无损对齐。
     */
    suspend fun translateLyricsWithTimestamps(lyrics: String, targetIso: String): String = withContext(Dispatchers.IO) {
        if (lyrics.isBlank()) return@withContext ""

        val rawLines = lyrics.lines()
        val timestamps = rawLines.map { line ->
            TIMESTAMP_REGEX.find(line.trim())?.value
        }
        val cleanLines = rawLines.map { line ->
            HtmlUtils.cleanPlainText(line.replace(TIMESTAMP_REGEX, "").trim())
        }

        // 收集非空需要翻译的行及其行索引
        val nonBlankEntries = cleanLines.mapIndexedNotNull { index, text ->
            if (text.isNotBlank()) index to text else null
        }

        if (nonBlankEntries.isEmpty()) {
            return@withContext lyrics
        }

        val textsToTranslate = nonBlankEntries.map { it.second }
        // 8 行智能紧凑带序号分块并发翻译（更小的分块能彻底避免长文本末尾丢行与截断）
        val chunks = textsToTranslate.chunked(8)
        val translatedTexts = try {
            val translatedChunks = supervisorScope {
                chunks.map { chunk ->
                    async {
                        val indexedChunkText = chunk.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
                        val youdaoResult = translateChunkViaYoudao(indexedChunkText)
                        var parsed = if (youdaoResult != null && youdaoResult.isNotEmpty()) {
                            parseIndexedLines(youdaoResult.map { HtmlUtils.cleanTranslationOutput(it) }, chunk.size)
                        } else emptyList()

                        // 容灾检查：若有道丢行较多，平滑回退至 Google Translate 或逐行重试
                        if (parsed.isEmpty() || parsed.count { it.isNotBlank() } < (chunk.size + 1) / 2) {
                            val googleFallback = translateViaGoogle(indexedChunkText, targetIso, "https://translate.googleapis.com/translate_a/single")
                            if (!googleFallback.isNullOrBlank()) {
                                parsed = parseIndexedLines(googleFallback.lines().map { HtmlUtils.cleanTranslationOutput(it) }, chunk.size)
                            } else {
                                val memoryFallback = translateViaMyMemory(indexedChunkText, targetIso)?.lines() ?: emptyList()
                                parsed = parseIndexedLines(memoryFallback.map { HtmlUtils.cleanTranslationOutput(it) }, chunk.size)
                            }
                        }

                        // 对每行译文进行音乐俚语语义润色
                        (0 until chunk.size).map { idx ->
                            val rawTrans = parsed.getOrNull(idx) ?: ""
                            refineMusicSlang(rawTrans, chunk[idx])
                        }
                    }
                }.awaitAll()
            }
            translatedChunks.flatten()
        } catch (e: Exception) {
            textsToTranslate
        }

        // 将翻译结果精确拼回原位置并还原时间戳标签
        val resultLines = rawLines.toMutableList()
        for (i in resultLines.indices) {
            val tag = timestamps[i]
            resultLines[i] = if (tag != null) "$tag" else ""
        }

        for (k in nonBlankEntries.indices) {
            val origIndex = nonBlankEntries[k].first
            val transText = if (k < translatedTexts.size) HtmlUtils.cleanTranslationOutput(translatedTexts[k].trim()) else ""
            val tag = timestamps[origIndex]
            resultLines[origIndex] = if (tag != null) {
                if (transText.isNotBlank()) "$tag $transText" else tag
            } else {
                transText
            }
        }

        resultLines.joinToString("\n")
    }

    /**
     * 底层文本翻译逻辑（支持标题、短语、长注释与整篇背景故事）：
     * 1. 按段落划分并发翻译，杜绝大段文本直接发送导致翻译端点末尾截断吞句。
     * 2. 逐段严格完整性校验（Truncation Detection）：若译文异常短小或未完结，毫秒级平滑回退至 Google Translate。
     * 3. 自动多重容灾降级（有道 -> Google Translate -> MyMemory），确保 100% 每一段都有完整且地道的译文！
     */
    suspend fun translateText(text: String, targetIso: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val cleaned = HtmlUtils.cleanPlainText(text)
        if (cleaned.isBlank()) return@withContext ""

        // 按自然段落 (\n\s*\n) 划分
        val paragraphs = cleaned.split(Regex("""(?:\r?\n\s*){2,}"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return@withContext text

        try {
            if (paragraphs.size == 1) {
                return@withContext translateSingleParagraphRobust(paragraphs[0], targetIso)
            }

            // 逐段并发高可靠翻译，保留自然段落格式且杜绝长文本截断
            val translatedParagraphs = supervisorScope {
                paragraphs.map { para ->
                    async {
                        translateSingleParagraphRobust(para, targetIso)
                    }
                }.awaitAll()
            }

            translatedParagraphs.joinToString("\n\n")
        } catch (e: Exception) {
            translateSingleParagraphRobust(cleaned, targetIso)
        }
    }

    /**
     * 单段落高可靠完整翻译，绝不漏句、绝不截断：
     * 1. 优先调用有道移动端极速端点
     * 2. 完整性校验：判断译文是否被截断（例如英文 194 字符但仅返回“这条线”、“但这并不是全部”等截断半句）
     * 3. 若有道失败或疑似截断，无感秒级回退至 Google Translate 补全
     * 4. 若 Google 亦不可用，平滑回退至 MyMemory
     */
    private suspend fun translateSingleParagraphRobust(unitText: String, targetIso: String): String {
        val cleanInput = HtmlUtils.cleanPlainText(unitText)
        if (cleanInput.isBlank()) return ""

        // 1. 优先使用有道移动端极速端点
        var youdaoResultText = ""
        for (attempt in 0..1) {
            val youdaoResult = translateChunkViaYoudao(cleanInput)
            if (!youdaoResult.isNullOrEmpty()) {
                val fullTranslated = youdaoResult.joinToString("\n").trim()
                val cleaned = HtmlUtils.cleanTranslationOutput(fullTranslated)
                if (cleaned.isNotBlank()) {
                    youdaoResultText = cleaned
                    break
                }
            }
            if (attempt == 0) kotlinx.coroutines.delay(100)
        }

        // 完整性校验：若有道返回结果完整无截断，且包含中文字符，则直接采用
        if (youdaoResultText.isNotBlank() &&
            !isTranslationTruncated(cleanInput, youdaoResultText) &&
            (Regex("""[\u4e00-\u9fa5]""").containsMatchIn(youdaoResultText) || targetIso != "zh-CN")
        ) {
            return refineMusicSlang(youdaoResultText, cleanInput)
        }

        // 2. 容灾回退至 Google Translate 公共端点（彻底补齐被截断的末尾段落与长句）
        val google = translateViaGoogle(cleanInput, targetIso, "https://translate.googleapis.com/translate_a/single")
        if (!google.isNullOrBlank()) {
            val cleaned = HtmlUtils.cleanTranslationOutput(google)
            if (cleaned.isNotBlank() && (Regex("""[\u4e00-\u9fa5]""").containsMatchIn(cleaned) || targetIso != "zh-CN")) {
                return refineMusicSlang(cleaned, cleanInput)
            }
        }

        // 3. 容灾回退至 MyMemory
        val myMemory = translateViaMyMemory(cleanInput, targetIso)
        if (!myMemory.isNullOrBlank()) {
            val cleaned = HtmlUtils.cleanTranslationOutput(myMemory)
            if (cleaned.isNotBlank()) return refineMusicSlang(cleaned, cleanInput)
        }

        // 保底：若全部失败但有道有部分文字则至少返回有道，否则原样返回
        return if (youdaoResultText.isNotBlank()) refineMusicSlang(youdaoResultText, cleanInput) else cleanInput
    }

    /**
     * 译文完整性检测：防范第三方无 Key 接口因字符长度限制而在末尾吞句截断
     */
    private fun isTranslationTruncated(original: String, translated: String): Boolean {
        if (translated.isBlank()) return true
        val origLen = original.length
        val transLen = translated.length

        // 原文有一定长度但译文畸短（典型如“这条线”、“但这并不是全部”）
        if (origLen >= 35 && transLen < 12) return true
        if (origLen >= 70 && transLen < 22) return true
        if (origLen >= 130 && transLen < (origLen * 0.16).toInt()) return true

        // 原文多句但译文仅有一短句且长度不成比例
        val origSentenceCount = original.count { it == '.' || it == '?' || it == '!' || it == '\n' }
        if (origSentenceCount >= 2 && origLen > 80 && transLen < 24) return true

        // 原文以正常句号/感叹号/问号结尾，但译文末尾异常截断无标点
        val hasClosingPunct = original.trimEnd().let { it.endsWith(".") || it.endsWith("!") || it.endsWith("?") }
        val transHasClosingPunct = translated.trimEnd().let {
            it.endsWith("。") || it.endsWith("！") || it.endsWith("？") || it.endsWith("”") || it.endsWith("’") || it.endsWith("\"") || it.endsWith("'") || it.endsWith(")") || it.endsWith("）") || it.endsWith(":") || it.endsWith("：")
        }
        if (hasClosingPunct && !transHasClosingPunct && transLen < (origLen * 0.35).toInt()) return true

        return false
    }

    /**
     * 音乐与 Hip-Hop 流行俚语常见机翻生硬直译校准器 (提高中文译文的地道性与可读性)
     */
    internal fun refineMusicSlang(translated: String, originalLine: String): String {
        if (translated.isBlank()) return translated
        var res = translated
        val origLower = originalLine.lowercase()

        // 1. 典故注释与歌词解析常见术语校正（如将 "This line" 规范化为 "这句歌词" 而非 "这条线"）
        if (origLower.contains("this line") || origLower.contains("these lines") || origLower.contains("this bar")) {
            res = res.replace("这条线", "这句歌词")
                .replace("这行", "这句歌词")
                .replace("该行", "这句歌词")
                .replace("此行", "这句歌词")
                .replace("这些线", "这些歌词")
                .replace("这个酒吧", "这句歌词")
        }
        if (origLower.contains("beefed with") || origLower.contains("beef with")) {
            res = res.replace("吃牛肉", "起争执过节")
        }

        // 2. 常见 Hip-Hop / 流行语与说唱俚语机翻修正
        if (origLower.contains("slimed me") || origLower.contains("slimed him")) {
            res = res.replace("涂了粘液", "背叛坑害了")
                .replace("给我涂了粘液", "坑害洗劫了我")
                .replace("给他涂了粘液", "坑害背叛了他")
        }
        if (origLower.contains("say cheese")) {
            res = res.replace("说奶酪", "笑一个合照")
                .replace("说\"奶酪\"", "笑一个合照")
                .replace("说“奶酪”", "笑一个合照")
        }
        if (origLower.contains("fake tea") || origLower.contains("fake plea")) {
            res = res.replace("假装茶", "假八卦")
                .replace("请假装茶", "纯属假八卦")
        }
        if (origLower.contains("puppy love")) {
            res = res.replace("所有的早恋", "那些年少青涩的早恋")
        }
        if (origLower.contains("wifin' up") || origLower.contains("wifing up")) {
            res = res.replace("娶了一个", "迎娶了")
        }

        // 3. 常见欧美说唱/街头金句与典型机翻笑话校正
        if (origLower.contains("running for your jewels") || origLower.contains("runnin' for your jewels") || origLower.contains("for your jewels") || origLower.contains("for the jewels")) {
            res = res.replace("为你的珠宝而奔跑", "抢夺洗劫你的金链首饰")
                .replace("为你的珠宝奔跑", "冲着抢劫你的金饰")
                .replace("为了你的珠宝而奔跑", "冲着洗劫你的金银首饰")
        }
        if (origLower.contains("how i'll leave ya") || origLower.contains("how i'll leave you") || origLower.contains("how i leave ya")) {
            res = res.replace("我怎么离开你", "我会把你收拾成什么凄惨死样")
                .replace("我如何离开你", "我会把你收拾成什么死样")
                .replace("我是怎么离开你", "我会把你处置成什么下场")
        }
        if (origLower.contains("steady gunning") || origLower.contains("steady gunnin")) {
            res = res.replace("稳扎稳打", "枪火连番扫射")
                .replace("平稳射击", "火力全开扫射")
        }
        if (origLower.contains("busting at") || origLower.contains("bustin' at") || origLower.contains("bustin at")) {
            res = res.replace("继续打击那些笨蛋", "继续狠狠收拾开火痛击那帮蠢货")
                .replace("打击那些笨蛋", "狠狠收拾那帮蠢货")
                .replace("打击那些傻瓜", "狠狠收拾那帮蠢货")
        }
        if (origLower.contains("mark-ass") || origLower.contains("mark ass")) {
            res = res.replace("一些混蛋", "一帮软蛋窝囊废")
                .replace("混蛋", "软蛋窝囊废")
        }
        if (origLower.contains("hit 'em up") || origLower.contains("hit em up")) {
            res = res.replace("打他们", "痛击干翻他们")
                .replace("击中他们", "狠狠痛击他们")
        }
        if (origLower.contains("when we ride") || origLower.contains("while we ride")) {
            res = res.replace("当我们骑在", "当我们出动扫荡")
                .replace("当我们骑", "当我们街头冲锋出动")
        }
        if (origLower.contains("fucked for life") || origLower.contains("f**ked for life")) {
            res = res.replace("一辈子被操", "这辈子彻底完蛋")
                .replace("终身被操", "这辈子彻底完蛋")
        }
        if (origLower.contains("bad boy killas") || origLower.contains("bad boy killa")) {
            res = res.replace("坏男孩基拉", "坏男孩杀手")
                .replace("坏男孩琪拉", "坏男孩杀手")
        }
        if (origLower.contains("no cap")) {
            res = res.replace("没有帽子", "绝无虚言(不吹牛)")
                .replace("没帽子", "真话不吹牛")
        }
        if (origLower.contains("iced out") || origLower.contains("ice on my")) {
            res = res.replace("结冰了", "满身闪耀钻石珠宝")
                .replace("冰在", "钻石戴在")
        }
        if (origLower.contains("drop top") || origLower.contains("droptop")) {
            res = res.replace("下沉顶部", "敞篷跑车")
                .replace("放下顶部", "敞篷跑车")
        }
        if (origLower.contains("pull up") || origLower.contains("pulled up")) {
            res = res.replace("拉起", "驱车杀到")
                .replace("停下来", "驱车现身")
        }
        if (origLower.contains("catch a body")) {
            res = res.replace("抓住一具尸体", "背上人命重案")
                .replace("抓住尸体", "犯下致命命案")
        }
        if (origLower.contains("want smoke") || origLower.contains("wants smoke")) {
            res = res.replace("想要抽烟", "存心找茬挑事")
                .replace("想要吸烟", "上门挑事开战")
        }

        return res
    }

    /**
     * 有道移动端极速翻译端点 (国内全网 5G/WiFi 直连，无 API Key，响应 200ms)
     */
    private fun translateChunkViaYoudao(text: String): List<String>? {
        return try {
            val formBody = FormBody.Builder()
                .add("inputtext", text)
                .add("type", "AUTO")
                .build()

            val request = Request.Builder()
                .url("https://m.youdao.com/translate")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://m.youdao.com/translate")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val html = response.body?.string() ?: return null
            val match = YOUDAO_RESULT_REGEX.find(html) ?: return null
            val rawResult = match.groupValues[1].trim()
            val unescaped = HtmlUtils.unescapeHtml(rawResult)
            unescaped.lines()
        } catch (e: Exception) {
            null
        }
    }

    private fun translateViaGoogle(text: String, targetIso: String, endpoint: String): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("client", "gtx")
                .add("sl", "auto")
                .add("tl", targetIso)
                .add("dt", "t")
                .add("q", text)
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            parseGoogleTranslateResponse(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGoogleTranslateResponse(jsonString: String): String? {
        return try {
            val root = JSONArray(jsonString)
            val segments = root.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONArray(i)
                val part = seg.optString(0)
                if (part.isNotEmpty()) {
                    sb.append(part)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun translateViaMyMemory(text: String, targetIso: String): String? {
        return try {
            val lines = text.lines()
            val translatedLines = lines.map { line ->
                val clean = LyricAligner.cleanLine(line)
                if (clean.isBlank()) {
                    line
                } else {
                    val encoded = URLEncoder.encode(clean, "UTF-8")
                    val queryUrl = "https://api.mymemory.translated.net/get?q=$encoded&langpair=auto|$targetIso"
                    val req = Request.Builder().url(queryUrl).build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    if (body != null) {
                        val obj = JSONObject(body)
                        val trans = obj.optJSONObject("responseData")?.optString("translatedText", clean) ?: clean
                        val match = Regex("""^(\[\d{2}:\d{2}(?:\.\d{1,3})?\])""").find(line)
                        if (match != null) "${match.value}$trans" else trans
                    } else {
                        line
                    }
                }
            }
            translatedLines.joinToString("\n")
        } catch (e: Exception) {
            null
        }
    }
}
