package com.linernotes.app.data.remote

import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricWordParser
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.HtmlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TranslationResult(
    val translatedTitle: String?,
    val translatedLyrics: String
)

/**
 * 高性能机器翻译服务 (Tencent TransMart 原生批量引擎 + 360 翻译 / Google / MyMemory 自动容灾降级)
 * 免 API Key、免科学上网（国内移动/联通/电信 5G 直连），自动保留时间轴与换行，极速响应 (100~350ms)。
 */
@Singleton
class TranslationService(
    private val targetLanguageProvider: () -> String
) {
    @Inject
    constructor(preferences: AiPreferences) : this({ preferences.targetLanguage })

    // For testing and fallback
    constructor() : this({ TranslationTargetLanguage.ZH_CN.code })

    // 共享 HTTP/2 / TLS 连接池：免去后续段落请求的重复握手耗时
    private val sharedConnectionPool = ConnectionPool(12, 5, TimeUnit.MINUTES)

    private val client = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .dispatcher(Dispatcher().apply {
            maxRequests = 48
            maxRequestsPerHost = 12
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    // 交互快车道：用户点击背景故事 / 典故解析时走独立 Dispatcher，绝不被后台预加载排队阻塞
    private val interactiveClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .dispatcher(Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 12
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    // Google Translate / MyMemory 紧凑超时客户端：海外毫秒级容灾，国内无代理环境下快速切出避免死等
    private val fastGoogleClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .dispatcher(Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 8
        })
        .connectTimeout(1800, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(1800, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build()

    private fun clientFor(highPriority: Boolean): OkHttpClient =
        if (highPriority) interactiveClient else client

    companion object {
        private const val CHUNK_LINE_COUNT = 15
        private const val PROSE_BATCH_MAX_ITEMS = 12
        private const val PROSE_BATCH_MAX_CHARS = 3200
        private const val MAX_360_FANYI_CHARS = 900
        private const val MAX_MYMEMORY_CHARS = 450
        private const val TRANSMART_URL = "https://transmart.qq.com/api/imt"
        private const val FANYI_360_URL = "https://fanyi.so.com/index/search"
        private val PARA_SPLIT_REGEX = Regex("""(?:\r?\n\s*){2,}""")
        private val KANA_REGEX = Regex("""[\u3040-\u30ff]""")
        private val HANGUL_REGEX = Regex("""[\uac00-\ud7af]""")
        private val CJK_REGEX = Regex("""[\u4e00-\u9fff]""")
        private val CYRILLIC_REGEX = Regex("""[\u0400-\u04ff]""")
        private val LATIN_REGEX = Regex("""[a-zA-Z]""")
        private val EXTENDED_LATIN_REGEX = Regex("""[À-ÖØ-öø-ÿ]""")
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
            val withoutTags = LyricWordParser.cleanInlineTimestamps(line.replace(TIMESTAMP_REGEX, ""))
            HtmlUtils.cleanPlainText(withoutTags.trim())
        }

        // 收集非空需要翻译的行及其行索引
        val nonBlankEntries = cleanLines.mapIndexedNotNull { index, text ->
            if (text.isNotBlank()) index to text else null
        }

        if (nonBlankEntries.isEmpty()) {
            return@withContext lyrics
        }

        val textsToTranslate = nonBlankEntries.map { it.second }
        // 18 行智能分块，配合 4 路并发与微延迟，兼顾长篇说唱曲目 (如 140+ 行) 的极速响应与防限流稳定性
        val chunks = textsToTranslate.chunked(18)
        val semaphore = Semaphore(4)
        val translatedTexts = try {
            val translatedChunks = supervisorScope {
                chunks.mapIndexed { chunkIdx, chunk ->
                    async {
                        semaphore.withPermit {
                            if (chunkIdx > 0) delay(25L)
                            val indexedChunkText = chunk.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
                            var parsed = emptyList<String>()

                            // 1. 优先尝试 Tencent TransMart 原生批量 JSON 翻译（国内直连 100~300ms，支持全球多语种，1:1 绝对对齐不串行）
                            val transMartResult = translateViaTransMart(chunk, targetIso)
                            if (!transMartResult.isNullOrEmpty() && transMartResult.size == chunk.size && transMartResult.any { it.isNotBlank() }) {
                                parsed = transMartResult
                            }

                            // 2. 备选：Google Translate 公共服务 (单次紧凑超时快速切换)
                            if (parsed.isEmpty() || parsed.count { it.isNotBlank() } < (chunk.size + 1) / 2) {
                                val googleFallback = translateViaGoogle(indexedChunkText, targetIso, "https://translate.googleapis.com/translate_a/single")
                                if (!googleFallback.isNullOrBlank()) {
                                    val candidate = parseIndexedLines(googleFallback.lines().map { HtmlUtils.cleanTranslationOutput(it) }, chunk.size)
                                    if (candidate.isNotEmpty() && candidate.count { it.isNotBlank() } > parsed.count { it.isNotBlank() }) {
                                        parsed = candidate
                                    }
                                }
                            }

                            // 3. 备选：360 翻译国内直连引擎（仅限英<->中互译，单句并发兜底）
                            if ((parsed.isEmpty() || parsed.count { it.isNotBlank() } < (chunk.size + 1) / 2) &&
                                supports360Fanyi(chunk.joinToString(" "), targetIso)
                            ) {
                                val fanyiSemaphore = Semaphore(4)
                                val fanyiLines = supervisorScope {
                                    chunk.mapIndexed { idx, line ->
                                        async {
                                            val existing = parsed.getOrNull(idx).orEmpty()
                                            if (existing.isNotBlank()) return@async existing
                                            fanyiSemaphore.withPermit {
                                                translateVia360Fanyi(line, targetIso, client).orEmpty()
                                            }
                                        }
                                    }.awaitAll()
                                }
                                if (fanyiLines.count { it.isNotBlank() } > parsed.count { it.isNotBlank() }) {
                                    parsed = fanyiLines
                                }
                            }

                            // 4. 严格防告警降级兜底：MyMemory
                            if (parsed.isEmpty() || parsed.count { it.isNotBlank() } < (chunk.size + 1) / 2) {
                                val memoryFallback = translateViaMyMemory(indexedChunkText, targetIso)?.lines() ?: emptyList()
                                val validMemory = memoryFallback.filterNot { isInvalidTranslation(it) }
                                if (validMemory.isNotEmpty() && validMemory.size == memoryFallback.size) {
                                    val candidate = parseIndexedLines(validMemory.map { HtmlUtils.cleanTranslationOutput(it) }, chunk.size)
                                    if (candidate.count { it.isNotBlank() } > parsed.count { it.isNotBlank() }) {
                                        parsed = candidate
                                    }
                                }
                            }

                            // 对每行译文进行音乐俚语语义润色与非法告警过滤
                            (0 until chunk.size).map { idx ->
                                val rawTrans = parsed.getOrNull(idx) ?: ""
                                if (isInvalidTranslation(rawTrans)) "" else refineMusicSlang(rawTrans, chunk[idx])
                            }
                        }
                    }
                }.awaitAll()
            }
            translatedChunks.flatten()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

        // 若全部引擎均失败且没有任何一行产出真实译文，直接返回空串，绝不回传“仅含时间戳”的伪非空字符串污染缓存！
        if (translatedTexts.none { it.isNotBlank() }) {
            return@withContext ""
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
     * 底层单篇文本翻译逻辑（标题、短语、长注释与整篇背景故事）：
     * - 自动按自然段落打包成单次 TransMart 原生批量请求（1 次往返完成整篇故事），
     * - 仅对批内缺失/失败段落执行多引擎容灾补全，彻底消除逐段串行请求拥塞。
     */
    suspend fun translateText(
        text: String,
        targetIso: String,
        highPriority: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        translateTextsBatch(listOf(text), targetIso, highPriority).firstOrNull()
    }

    /**
     * 高性能散文/典故批量翻译入口：
     * 将多条注释与背景故事的多自然段拆解并打包进少量 TransMart `text_list` 请求中，
     * 保持严格 1:1 段落结构映射，并在失败时仅针对缺失段落快速容灾切换。
     */
    suspend fun translateTextsBatch(
        texts: List<String>,
        targetIso: String,
        highPriority: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        val perItemParagraphs = texts.map { raw ->
            val cleaned = HtmlUtils.cleanPlainText(raw)
            if (cleaned.isBlank()) emptyList()
            else cleaned.split(PARA_SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }
        }

        val flatParagraphs = mutableListOf<String>()
        val itemRanges = mutableListOf<IntRange>()
        for (paras in perItemParagraphs) {
            val start = flatParagraphs.size
            flatParagraphs.addAll(paras)
            itemRanges.add(start until flatParagraphs.size)
        }

        if (flatParagraphs.isEmpty()) {
            return@withContext texts.map { "" }
        }

        val httpClient = clientFor(highPriority)
        val isChineseTarget = targetIso.startsWith("zh", ignoreCase = true)
        val chunks = buildProseBatchChunks(flatParagraphs)
        val flatResults = MutableList(flatParagraphs.size) { idx -> flatParagraphs[idx] }
        val chunkSemaphore = Semaphore(if (highPriority) 6 else 4)

        try {
            supervisorScope {
                chunks.map { indexBatch ->
                    async {
                        chunkSemaphore.withPermit {
                            val subInputs = indexBatch.map { flatParagraphs[it] }
                            val transMartBatch = translateViaTransMart(
                                lines = subInputs,
                                targetIso = targetIso,
                                isLyrics = false,
                                httpClient = httpClient
                            )

                            val missingLocalIndices = mutableListOf<Int>()
                            for (localIdx in subInputs.indices) {
                                val origPara = subInputs[localIdx]
                                val rawCandidate = transMartBatch?.getOrNull(localIdx)
                                val accepted = validateTranslatedParagraph(origPara, rawCandidate, isChineseTarget)
                                if (accepted != null) {
                                    flatResults[indexBatch[localIdx]] = accepted
                                } else {
                                    missingLocalIndices.add(localIdx)
                                }
                            }

                            if (missingLocalIndices.isNotEmpty()) {
                                missingLocalIndices.map { localIdx ->
                                    async {
                                        val globalIdx = indexBatch[localIdx]
                                        flatResults[globalIdx] = translateParagraphFallback(
                                            cleanInput = flatParagraphs[globalIdx],
                                            targetIso = targetIso,
                                            httpClient = httpClient
                                        )
                                    }
                                }.awaitAll()
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        texts.indices.map { i ->
            val range = itemRanges[i]
            if (range.isEmpty()) {
                ""
            } else {
                range.joinToString("\n\n") { idx -> flatResults[idx] }
            }
        }
    }

    internal fun buildProseBatchChunks(
        paragraphs: List<String>,
        maxItems: Int = PROSE_BATCH_MAX_ITEMS,
        maxTotalChars: Int = PROSE_BATCH_MAX_CHARS
    ): List<List<Int>> {
        if (paragraphs.isEmpty()) return emptyList()
        val batches = mutableListOf<List<Int>>()
        var currentBatch = mutableListOf<Int>()
        var currentChars = 0

        for (idx in paragraphs.indices) {
            val len = paragraphs[idx].length
            if (currentBatch.isNotEmpty() &&
                (currentBatch.size >= maxItems || currentChars + len > maxTotalChars)
            ) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentChars = 0
            }
            currentBatch.add(idx)
            currentChars += len
        }
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }
        return batches
    }

    private fun validateTranslatedParagraph(
        origInput: String,
        rawOutput: String?,
        isChineseTarget: Boolean
    ): String? {
        if (rawOutput.isNullOrBlank() || isInvalidTranslation(rawOutput)) return null
        val cleaned = HtmlUtils.cleanTranslationOutput(rawOutput)
            .replace(PARA_SPLIT_REGEX, "\n")
            .trim()
        if (cleaned.isBlank() ||
            isInvalidTranslation(cleaned) ||
            isTranslationTruncated(origInput, cleaned) ||
            (isChineseTarget && !CJK_REGEX.containsMatchIn(cleaned))
        ) {
            return null
        }
        return refineMusicSlang(cleaned, origInput)
    }

    /**
     * 仅针对批内少数异常/失败段落执行轻量容灾降级（无 12s 双域名阻塞等待）
     */
    private suspend fun translateParagraphFallback(
        cleanInput: String,
        targetIso: String,
        httpClient: OkHttpClient
    ): String {
        val isChineseTarget = targetIso.startsWith("zh", ignoreCase = true)

        // 1. 临时切换全新 clientKey 单段直连重试一次 TransMart（排查并发限流）
        val retryTransMart = executeTransMartBatch(
            textList = listOf(cleanInput),
            sourceLang = "auto",
            targetLang = mapTargetToTransMartLang(targetIso),
            httpClient = httpClient,
            clientKeyOverride = "browser-chrome-120.0.0-Android-${UUID.randomUUID()}"
        )?.firstOrNull()
        validateTranslatedParagraph(cleanInput, retryTransMart, isChineseTarget)?.let { return it }

        // 2. 360 翻译国内直连引擎（实测超 ~1000 字符会报错延迟，超过上限直接跳过避免白等 2 秒）
        if (cleanInput.length <= MAX_360_FANYI_CHARS && supports360Fanyi(cleanInput, targetIso)) {
            val fanyi = translateVia360Fanyi(cleanInput, targetIso, httpClient)
            validateTranslatedParagraph(cleanInput, fanyi, isChineseTarget)?.let { return it }
        }

        // 3. Google Translate 单次快速尝试（compact 4s timeout）
        val google = translateViaGoogle(cleanInput, targetIso, "https://translate.googleapis.com/translate_a/single")
        if (!google.isNullOrBlank()) {
            val cleaned = HtmlUtils.cleanTranslationOutput(google).replace(PARA_SPLIT_REGEX, "\n").trim()
            if (cleaned.isNotBlank() && !isInvalidTranslation(cleaned) &&
                (!isChineseTarget || CJK_REGEX.containsMatchIn(cleaned))
            ) {
                return refineMusicSlang(cleaned, cleanInput)
            }
        }

        // 4. MyMemory 短文本单请求快速兜底（避免长文逐行超时）
        if (cleanInput.length <= MAX_MYMEMORY_CHARS) {
            val singleLine = cleanInput.replace('\n', ' ')
            val myMemory = translateViaMyMemory(singleLine, targetIso)
            if (!myMemory.isNullOrBlank() && !isInvalidTranslation(myMemory)) {
                val cleaned = HtmlUtils.cleanTranslationOutput(myMemory).replace(PARA_SPLIT_REGEX, "\n").trim()
                if (cleaned.isNotBlank() && !isInvalidTranslation(cleaned) &&
                    (!isChineseTarget || CJK_REGEX.containsMatchIn(cleaned))
                ) {
                    return refineMusicSlang(cleaned, cleanInput)
                }
            }
        }

        return cleanInput
    }

    /**
     * 译文严重截断检测：仅拦截极端残缺废句，避免正常精炼中译英（~0.25~0.33 字符比）被误杀
     */
    internal fun isTranslationTruncated(original: String, translated: String): Boolean {
        if (translated.isBlank()) return true
        val origLen = original.length
        val transLen = translated.length

        if (origLen >= 60 && transLen < 10) return true
        if (origLen >= 160 && transLen < 18) return true
        if (origLen >= 300 && transLen < (origLen * 0.12).toInt()) return true

        val origSentenceCount = original.count { it == '.' || it == '?' || it == '!' || it == '\n' }
        if (origSentenceCount >= 3 && origLen > 120 && transLen < 20) return true

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

        // 3. 通用 Hip-Hop / 流行文化高频俚语与行话校正
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

    private val transMartClientKey: String by lazy {
        "browser-chrome-120.0.0-Android-${UUID.randomUUID()}-${System.currentTimeMillis()}"
    }

    fun isInvalidTranslation(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase()
        return lower.contains("mymemory") ||
                lower.contains("translated.net") ||
                lower.contains("usage limit") ||
                lower.contains("usagelimits") ||
                lower.contains("free translations") ||
                lower.contains("rate limit") ||
                lower.contains("too many requests") ||
                lower.contains("quota exceeded") ||
                lower.contains("403 forbidden") ||
                lower.contains("429 too many") ||
                lower.contains("service unavailable") ||
                lower.contains("invalid source language") ||
                lower.contains("is an invalid target language")
    }

    internal fun mapTargetToTransMartLang(targetIso: String): String {
        return when (targetIso.lowercase()) {
            "zh-cn", "zh", "zh-hans" -> "zh"
            "zh-tw", "zh-hk", "zh-hant" -> "zh-TW"
            else -> targetIso.substringBefore("-").lowercase()
        }
    }

    internal fun detectLineScriptBucket(line: String): String = when {
        KANA_REGEX.containsMatchIn(line) -> "ja"
        HANGUL_REGEX.containsMatchIn(line) -> "ko"
        CYRILLIC_REGEX.containsMatchIn(line) -> "ru"
        CJK_REGEX.containsMatchIn(line) -> "zh"
        EXTENDED_LATIN_REGEX.containsMatchIn(line) -> "auto"
        LATIN_REGEX.containsMatchIn(line) -> "en"
        else -> "auto"
    }

    /**
     * Tencent TransMart 公共批量翻译引擎：
     * - 同文字体批次直接走 `auto` 自动识别（保留西/法/德等纯 ASCII 与带重音混排句子的整首语境）
     * - 若同一批混杂日/韩/中与纯英副歌，则按语系分桶平移翻译，防止主语种吞并英文副歌
     */
    private fun translateViaTransMart(
        lines: List<String>,
        targetIso: String,
        isLyrics: Boolean = true,
        httpClient: OkHttpClient = client
    ): List<String>? {
        if (lines.isEmpty()) return emptyList()
        val tgtLang = mapTargetToTransMartLang(targetIso)
        val cleanInputs = lines.map { raw ->
            val cleaned = if (isLyrics) LyricAligner.cleanLine(raw) else HtmlUtils.cleanPlainText(raw)
            cleaned.ifBlank { " " }
        }
        val buckets = cleanInputs.map { detectLineScriptBucket(it) }
        val distinctScripts = buckets.toSet()
        val hasAsianAndLatinMix = distinctScripts.any { it == "ja" || it == "ko" || it == "zh" } &&
            distinctScripts.any { it == "en" || it == "auto" }

        return if (!hasAsianAndLatinMix) {
            executeTransMartBatch(cleanInputs, sourceLang = "auto", targetLang = tgtLang, httpClient = httpClient)
        } else {
            val out = MutableList(cleanInputs.size) { "" }
            val groupedIndices = cleanInputs.indices.groupBy { idx ->
                val b = buckets[idx]
                if (b == "zh" && tgtLang.startsWith("zh")) "auto" else b
            }
            for ((srcLang, indices) in groupedIndices) {
                val subLines = indices.map { cleanInputs[it] }
                val translated = executeTransMartBatch(
                    textList = subLines,
                    sourceLang = srcLang,
                    targetLang = tgtLang,
                    httpClient = httpClient
                ) ?: continue
                indices.forEachIndexed { pos, origIdx ->
                    out[origIdx] = translated.getOrNull(pos).orEmpty()
                }
            }
            if (out.any { it.isNotBlank() }) out else null
        }
    }

    private fun executeTransMartBatch(
        textList: List<String>,
        sourceLang: String,
        targetLang: String,
        httpClient: OkHttpClient = client,
        clientKeyOverride: String? = null
    ): List<String>? {
        if (textList.isEmpty()) return emptyList()
        return try {
            val payload = JSONObject().apply {
                put("header", JSONObject().apply {
                    put("fn", "auto_translation")
                    put("client_key", clientKeyOverride ?: transMartClientKey)
                })
                put("type", "plain")
                put("model_category", "normal")
                put("source", JSONObject().apply {
                    put("lang", sourceLang)
                    put("text_list", JSONArray(textList))
                })
                put("target", JSONObject().apply {
                    put("lang", targetLang)
                })
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(TRANSMART_URL)
                .header("Referer", "https://transmart.qq.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(body)
                .build()

            val respStr = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            } ?: return null

            val root = JSONObject(respStr)
            val retCode = root.optJSONObject("header")?.optString("ret_code", "")
            if (retCode != "succ") return null
            val arr = root.optJSONArray("auto_translation") ?: return null
            if (arr.length() != textList.size) return null

            (0 until arr.length()).map { i ->
                val decoded = HtmlUtils.unescapeHtml(arr.optString(i, "").trim())
                if (isInvalidTranslation(decoded)) "" else decoded
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun supports360Fanyi(text: String, targetIso: String): Boolean {
        if (KANA_REGEX.containsMatchIn(text) || HANGUL_REGEX.containsMatchIn(text)) return false
        val isZhTarget = targetIso.startsWith("zh", ignoreCase = true)
        val isEnTarget = targetIso.startsWith("en", ignoreCase = true)
        val hasCjk = CJK_REGEX.containsMatchIn(text)
        return (isZhTarget && !hasCjk && LATIN_REGEX.containsMatchIn(text)) || (isEnTarget && hasCjk)
    }

    /**
     * 360 翻译国内直连兜底引擎（仅限英文<->中文互译）
     */
    private fun translateVia360Fanyi(
        text: String,
        targetIso: String,
        httpClient: OkHttpClient = client
    ): String? {
        val clean = HtmlUtils.cleanPlainText(text)
        if (clean.isBlank() || clean.length > MAX_360_FANYI_CHARS || !supports360Fanyi(clean, targetIso)) return null
        return try {
            val isZhToEn = targetIso.startsWith("en", ignoreCase = true) && CJK_REGEX.containsMatchIn(clean)
            val formBody = FormBody.Builder()
                .add("eng", if (isZhToEn) "0" else "1")
                .add("validate", "")
                .add("ignore_trans", "0")
                .add("query", clean)
                .build()

            val request = Request.Builder()
                .url(FANYI_360_URL)
                .post(formBody)
                .header("pro", "fanyi")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val respStr = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            } ?: return null

            val root = JSONObject(respStr)
            if (root.optInt("error", -1) != 0) return null
            val fanyi = root.optJSONObject("data")?.optString("fanyi", "")?.trim() ?: ""
            if (fanyi.isBlank() || isInvalidTranslation(fanyi)) return null
            HtmlUtils.unescapeHtml(fanyi)
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

            val body = fastGoogleClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            } ?: return null
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
            val translatedLines = mutableListOf<String>()
            for (line in lines) {
                val clean = LyricAligner.cleanLine(line)
                if (clean.isBlank()) {
                    translatedLines.add(line)
                } else {
                    val encoded = URLEncoder.encode(clean, "UTF-8")
                    val detectedSrc = detectLineScriptBucket(clean).let { if (it == "auto") "en" else it }
                    val queryUrl = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$detectedSrc|$targetIso"
                    val req = Request.Builder().url(queryUrl).build()
                    val body = fastGoogleClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return null
                        resp.body?.string()
                    } ?: return null
                    val obj = JSONObject(body)
                    val status = obj.optInt("responseStatus", 200)
                    if (status != 200) {
                        // 配额耗尽或服务拒绝，立即熔断返回 null，绝不继续请求
                        return null
                    }
                    val trans = obj.optJSONObject("responseData")?.optString("translatedText", "") ?: ""
                    if (trans.isBlank() || isInvalidTranslation(trans)) {
                        // 发现配额告警或空译文，立即熔断
                        return null
                    }
                    val match = Regex("""^(\[\d{1,2}:\d{2}(?:\.\d{1,3})?\])""").find(line)
                    val lineTrans = if (match != null) "${match.value}$trans" else trans
                    translatedLines.add(lineTrans)
                }
            }
            translatedLines.joinToString("\n")
        } catch (e: Exception) {
            null
        }
    }
}
