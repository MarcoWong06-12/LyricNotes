package com.linernotes.app.core.lyric

import com.linernotes.app.domain.model.BilingualLyricLine

object LyricAligner {

    val LRC_TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}(?:\.\d{2,3})?]""")
    val LRC_METADATA_REGEX = Regex("""^\[(ti|ar|al|by|offset|length|re|ve|encoding):.*?]""", RegexOption.IGNORE_CASE)

    fun cleanLine(line: String): String {
        val withoutTime = line.replace(LRC_TIMESTAMP_REGEX, "").trim()
        if (withoutTime.matches(LRC_METADATA_REGEX)) {
            return ""
        }
        return withoutTime
    }

    fun extractTimestampMs(line: String): Long? {
        val match = TIMESTAMP_PARSER_REGEX.find(line) ?: return null
        val min = match.groupValues[1].toLongOrNull() ?: 0L
        val sec = match.groupValues[2].toLongOrNull() ?: 0L
        val msStr = match.groupValues.getOrNull(3) ?: ""
        val ms = when (msStr.length) {
            2 -> (msStr.toLongOrNull() ?: 0L) * 10
            3 -> msStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return min * 60_000L + sec * 1000L + ms
    }

    fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val frac = (ms % 1000) / 10
        return String.format(java.util.Locale.US, "[%02d:%02d.%02d]", min, sec, frac)
    }

    fun isRefusalText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val refusalMarkers = listOf(
            "无法逐行翻译", "無法逐行翻譯",
            "受版权保护", "受版權保護",
            "版权原因", "版權原因",
            "侵犯版权", "侵犯版權",
            "版权所有", "版權所有",
            "90 个字符", "90 個字元", "90个字符", "90個字元",
            "主题摘要", "主題摘要", "意象与情绪", "意象與情緒",
            "copyrighted", "copyright protection", "copyright infringement",
            "cannot translate", "unable to translate", "cannot reproduce"
        )
        val lower = text.lowercase()
        if (refusalMarkers.any { lower.contains(it) }) {
            return true
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size in 1..3) {
            val apologyPrefixes = listOf(
                "抱歉", "對不起", "对不起", "很抱歉", "非常抱歉",
                "sorry", "i apologize", "as an ai", "i cannot"
            )
            if (apologyPrefixes.any { lines.first().startsWith(it, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    /**
     * 将存储的纯文本或 LRC 歌词对齐解析为逐行双语模型，
     * 具备时间戳提取、多时间标签展开与单调序列动态规划容错对齐：
     * 彻底杜绝时间戳漂移、平台延迟导致的错行、跳行与多米诺骨牌式连锁位移。
     */
    fun align(originalRaw: String?, translatedRaw: String?): List<BilingualLyricLine> {
        if (originalRaw.isNullOrBlank() && translatedRaw.isNullOrBlank()) {
            return emptyList()
        }

        val cleanOriginal = sanitizeText(originalRaw)
        val cleanTranslated = sanitizeText(translatedRaw)

        // 若本地存储的内容实际上是 AI 触发版权限制后的拒识文本，自动视为空，避免污染歌词界面
        val safeTranslated = if (isRefusalText(cleanTranslated)) "" else cleanTranslated

        // 展开单行多时间戳 (如 [00:10.00][01:10.00] 唱词)，并按时间轴重排保证时间单调递增
        val expandedOriginal = expandMultiTimestampLines(cleanOriginal)
        val rawOrigLines = expandedOriginal.lines()
        val rawTransLines = safeTranslated.lines()

        val origLines = rawOrigLines.map { cleanLine(it) }
        val transLines = rawTransLines.map { cleanLine(it) }
        val origTimes = rawOrigLines.map { extractTimestampMs(it) }
        val transTimes = rawTransLines.map { extractTimestampMs(it) }

        if (transLines.isEmpty() || transLines.all { it.isBlank() }) {
            // 没有翻译时，纯净展示原文
            return origLines.mapIndexed { index, line ->
                BilingualLyricLine(
                    lineNumber = index + 1,
                    original = line,
                    translation = "",
                    isStanzaBreak = line.isBlank(),
                    startTimeMs = origTimes.getOrNull(index)
                )
            }
        }

        // 1. 优先检查译文中是否含有行号标记（如 "1. "、"[#1] " 等），实现确定性绝对锚定
        val indexRegex = Regex("""^\s*(?:\[#?(\d+)\]|(\d+)[\.、\s\-:])\s*(.*)$""")
        val indexedTransMap = mutableMapOf<Int, String>()
        var hasNumberedTrans = false

        for (line in transLines) {
            val m = indexRegex.find(line.trim())
            if (m != null) {
                val num = (m.groupValues[1].toIntOrNull() ?: m.groupValues[2].toIntOrNull())
                val text = m.groupValues[3].trim()
                if (num != null) {
                    indexedTransMap[num] = text
                    hasNumberedTrans = true
                }
            }
        }

        if (hasNumberedTrans && indexedTransMap.size >= (transLines.count { it.isNotBlank() }.coerceAtLeast(1) / 2)) {
            val result = ArrayList<BilingualLyricLine>()
            var nonBlankSeq = 1
            for (i in origLines.indices) {
                val orig = origLines[i]
                val time = origTimes.getOrNull(i)
                if (orig.isBlank()) {
                    result.add(
                        BilingualLyricLine(
                            lineNumber = i + 1,
                            original = "",
                            translation = "",
                            isStanzaBreak = true,
                            startTimeMs = time
                        )
                    )
                } else {
                    val trans = indexedTransMap[nonBlankSeq++] ?: ""
                    result.add(
                        BilingualLyricLine(
                            lineNumber = i + 1,
                            original = orig,
                            translation = trans,
                            isStanzaBreak = false,
                            startTimeMs = time
                        )
                    )
                }
            }
            return result
        }

        // 2. 提取原歌词与译文的全部非空演唱正文行
        val nonBlankOrig = origLines.mapIndexedNotNull { idx, text ->
            if (text.isNotBlank()) AlignIndexedLine(idx, text, origTimes.getOrNull(idx)) else null
        }
        val nonBlankTrans = transLines.mapIndexedNotNull { idx, text ->
            if (text.isNotBlank()) AlignIndexedLine(idx, text, transTimes.getOrNull(idx)) else null
        }

        val matchedTrans = MutableList(origLines.size) { "" }

        if (nonBlankOrig.isNotEmpty() && nonBlankTrans.isNotEmpty()) {
            if (nonBlankOrig.size == nonBlankTrans.size) {
                // 黄金对齐通道 (1:1 顺序映射)：
                // 当双方非空唱词行数一致时（绝大多数逐行翻译场景），直接进行 1:1 单调对齐。
                // 即使译文的时间标签存在在线平台的数秒偏差或延迟，也绝不发生错行错配或跳行！
                for (k in nonBlankOrig.indices) {
                    val origIdx = nonBlankOrig[k].index
                    matchedTrans[origIdx] = nonBlankTrans[k].text
                }
            } else {
                // 动态规划序列对齐 (Needleman-Wunsch / Monotonic Sequence Alignment)：
                // 支持行数不完全一致（如原歌词有伴奏重复句，或译文合并/拆分行），
                // 综合时间戳邻近度与全曲相对进度百分比，寻找全局单调递增最优解，彻底消除多米诺骨牌式错位！
                val matchMap = alignSequencesDP(nonBlankOrig, nonBlankTrans)
                for ((origIdx, transText) in matchMap) {
                    matchedTrans[origIdx] = transText
                }
            }
        }

        val result = ArrayList<BilingualLyricLine>()
        for (i in origLines.indices) {
            val orig = origLines[i]
            val time = origTimes.getOrNull(i)
            val trans = matchedTrans[i]
            result.add(
                BilingualLyricLine(
                    lineNumber = i + 1,
                    original = orig,
                    translation = trans,
                    isStanzaBreak = orig.isBlank() && trans.isBlank(),
                    startTimeMs = time
                )
            )
        }
        return result
    }

    private data class AlignIndexedLine(val index: Int, val text: String, val timeMs: Long?)

    /**
     * 单调递增序列动态规划对齐引擎：
     * 针对原歌词与翻译行数不完全一致的边界场景，在时间戳差值与全篇相对进度双重惩罚约束下，
     * 求解全局代价最小的单调匹配路径，绝不倒退、绝不交错。
     */
    private fun alignSequencesDP(
        origList: List<AlignIndexedLine>,
        transList: List<AlignIndexedLine>
    ): Map<Int, String> {
        val n = origList.size
        val m = transList.size
        if (n == 0 || m == 0) return emptyMap()

        // dp[i][j]: minimum alignment cost between origList[0 until i] and transList[0 until j]
        val dp = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dp[0][0] = 0f
        // parent[i][j]: 0 = match, 1 = skip orig, 2 = skip trans
        val parent = Array(n + 1) { IntArray(m + 1) { -1 } }

        for (i in 0..n) {
            for (j in 0..m) {
                if (i == 0 && j == 0) continue
                var minCost = Float.MAX_VALUE
                var bestAction = -1

                // 动作 1: 跳过原文行 (原歌词有额外伴奏/垫音行)
                if (i > 0 && dp[i - 1][j] != Float.MAX_VALUE) {
                    val cost = dp[i - 1][j] + 500f
                    if (cost < minCost) {
                        minCost = cost
                        bestAction = 1
                    }
                }

                // 动作 2: 跳过译文行 (译文有冗余行)
                if (j > 0 && dp[i][j - 1] != Float.MAX_VALUE) {
                    val cost = dp[i][j - 1] + 500f
                    if (cost < minCost) {
                        minCost = cost
                        bestAction = 2
                    }
                }

                // 动作 3: 匹配 orig[i-1] 与 trans[j-1]
                if (i > 0 && j > 0 && dp[i - 1][j - 1] != Float.MAX_VALUE) {
                    val ot = origList[i - 1].timeMs
                    val tt = transList[j - 1].timeMs

                    val timeCost = if (ot != null && tt != null) {
                        (kotlin.math.abs(ot - tt) / 10f).coerceAtMost(1000f)
                    } else 0f

                    val posDiff = kotlin.math.abs((i - 1f) / n - (j - 1f) / m) * 800f
                    val matchCost = dp[i - 1][j - 1] + timeCost + posDiff
                    if (matchCost < minCost) {
                        minCost = matchCost
                        bestAction = 0
                    }
                }

                dp[i][j] = minCost
                parent[i][j] = bestAction
            }
        }

        // 回溯寻找最优匹配路径
        var currI = n
        var currJ = m
        val matchedMap = mutableMapOf<Int, String>()

        while (currI > 0 || currJ > 0) {
            when (parent[currI][currJ]) {
                0 -> {
                    val origItem = origList[currI - 1]
                    val transItem = transList[currJ - 1]
                    matchedMap[origItem.index] = transItem.text
                    currI--
                    currJ--
                }
                1 -> {
                    currI--
                }
                2 -> {
                    currJ--
                }
                else -> break
            }
        }

        return matchedMap
    }

    /**
     * 将单行包含多个时间标签的 LRC (如 [00:10.00][01:10.00] 唱词) 展开为按时间轴单调递增的独立单句
     */
    fun expandMultiTimestampLines(lrc: String): String {
        val lines = lrc.lines()
        val result = mutableListOf<String>()
        var hasMulti = false
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank() || trimmed.matches(LRC_METADATA_REGEX)) {
                result.add(rawLine)
                continue
            }
            val matches = TIMESTAMP_PARSER_REGEX.findAll(trimmed).toList()
            if (matches.size <= 1) {
                result.add(rawLine)
            } else {
                hasMulti = true
                val cleanText = trimmed.replace(TIMESTAMP_PARSER_REGEX, "").trim()
                for (m in matches) {
                    result.add("${m.value} $cleanText")
                }
            }
        }
        if (!hasMulti) return lrc

        val timed = mutableListOf<Pair<Long?, String>>()
        for (line in result) {
            val time = extractTimestampMs(line)
            timed.add(time to line)
        }
        val sorted = timed.sortedWith(compareBy(nullsLast()) { it.first })
        return sorted.joinToString("\n") { it.second }
    }

    private fun splitIntoParagraphs(lines: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    result.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current)
        }
        return if (result.isEmpty()) listOf(emptyList()) else result
    }

    data class TimedLyric(val ms: Long, val text: String)

    private val TIMESTAMP_PARSER_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?\]""")

    private val CREDIT_FILTER_REGEX = Regex(
        """(作词|作曲|编曲|制作人|监制|混音|母带|录音|吉他|贝斯|鼓|键盘|和音|弦乐|OP|SP|Producer|Writers|Written\s*by|Lyrics\s*by|Composed\s*by|Arranged\s*by|Mixed\s*by|Mastered\s*by|Sample|by:)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 将网易云等平台返回的包含 [mm:ss.xx] 时间戳的原版歌词与翻译歌词，
     * 通过毫秒级时间戳精准匹配并消除制作人名单，输出 1:1 行对齐且保留时间标签的歌词文本。
     */
    fun alignLrcTimestamps(origLrc: String, transLrc: String?): Pair<String, String> {
        val origTimed = parseTimedLines(origLrc)
        val transTimed = if (!transLrc.isNullOrBlank()) parseTimedLines(transLrc) else emptyList()

        // 过滤开头的制作信息行与翻译者信息（前 3.5 秒内包含制作人、作词作曲等纯制作信息）
        val filteredOrig = origTimed.filterNot { item ->
            item.ms < 3500 && item.text.contains(CREDIT_FILTER_REGEX)
        }
        val filteredTrans = transTimed.filterNot { item ->
            item.ms < 3500 && item.text.contains(CREDIT_FILTER_REGEX)
        }

        if (filteredOrig.isEmpty()) {
            val fallbackClean = origLrc.lines().map { cleanLine(it) }.filter { it.isNotBlank() }.joinToString("\n")
            return Pair(fallbackClean, "")
        }

        if (filteredTrans.isEmpty()) {
            val origResult = StringBuilder()
            for (i in filteredOrig.indices) {
                val current = filteredOrig[i]
                if (i > 0 && current.ms - filteredOrig[i - 1].ms >= 6500) {
                    origResult.append("\n")
                }
                val tag = formatTimestamp(current.ms)
                origResult.append(tag).append(" ").append(current.text).append("\n")
            }
            return Pair(origResult.toString().trimEnd(), "")
        }

        val origResult = StringBuilder()
        val transResult = StringBuilder()
        var lastUsedTransIndex = -1

        for (i in filteredOrig.indices) {
            val current = filteredOrig[i]
            if (i > 0) {
                val prev = filteredOrig[i - 1]
                // 若两句歌词之间间隔超过 6.5 秒，插入空行分段
                if (current.ms - prev.ms >= 6500) {
                    origResult.append("\n")
                    transResult.append("\n")
                }
            }

            // 查找时间戳最匹配且偏差在 600ms 内的对应译文行（单调递增匹配，防止时间跳回）
            var matchedTrans: TimedLyric? = null
            var matchedIndex = -1
            var bestDiff = 600L

            for (j in filteredTrans.indices) {
                if (j <= lastUsedTransIndex) continue
                val cand = filteredTrans[j]
                val diff = kotlin.math.abs(cand.ms - current.ms)
                if (diff <= bestDiff) {
                    bestDiff = diff
                    matchedTrans = cand
                    matchedIndex = j
                }
            }

            if (matchedIndex != -1) {
                lastUsedTransIndex = matchedIndex
            }

            val transText = matchedTrans?.text?.trim() ?: ""

            val tag = formatTimestamp(current.ms)
            origResult.append(tag).append(" ").append(current.text).append("\n")
            if (transText.isNotBlank()) {
                transResult.append(tag).append(" ").append(transText).append("\n")
            } else {
                transResult.append("\n")
            }
        }

        return Pair(origResult.toString().trimEnd(), transResult.toString().trimEnd())
    }

    private fun parseTimedLines(lrc: String): List<TimedLyric> {
        val result = mutableListOf<TimedLyric>()
        for (rawLine in lrc.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.matches(LRC_METADATA_REGEX)) continue

            val matches = TIMESTAMP_PARSER_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val cleanText = trimmed.replace(TIMESTAMP_PARSER_REGEX, "").trim()
            if (cleanText.isEmpty()) continue

            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val msStr = m.groupValues.getOrNull(3) ?: ""
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = min * 60_000L + sec * 1000L + ms
                result.add(TimedLyric(totalMs, cleanText))
            }
        }
        result.sortBy { it.ms }
        return result
    }

    private fun sanitizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val lines = text.lines().toMutableList()
        // 移除开头与结尾可能存在的 markdown 代码块标签（如 ``` 或 ```markdown）
        while (lines.isNotEmpty() && lines.first().trim().startsWith("```")) {
            lines.removeAt(0)
        }
        while (lines.isNotEmpty() && lines.last().trim().startsWith("```")) {
            lines.removeAt(lines.size - 1)
        }
        return lines.joinToString("\n")
    }
}
