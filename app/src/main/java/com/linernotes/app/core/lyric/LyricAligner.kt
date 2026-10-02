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
     * 具备时间戳提取与智能段落容错对齐：彻底杜绝由于模型遗漏空行导致后续全部错位的问题。
     */
    fun align(originalRaw: String?, translatedRaw: String?): List<BilingualLyricLine> {
        if (originalRaw.isNullOrBlank() && translatedRaw.isNullOrBlank()) {
            return emptyList()
        }

        val cleanOriginal = sanitizeText(originalRaw)
        val cleanTranslated = sanitizeText(translatedRaw)

        // 若本地存储的内容实际上是 AI 触发版权限制后的拒识文本，自动视为空，避免污染歌词界面
        val safeTranslated = if (isRefusalText(cleanTranslated)) "" else cleanTranslated

        val rawOrigLines = cleanOriginal.lines()
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

        val hasOrigTimestamps = origTimes.any { it != null }
        val hasTransTimestamps = transTimes.any { it != null }

        val result = ArrayList<BilingualLyricLine>()

        if (hasOrigTimestamps && hasTransTimestamps) {
            // 双通道高精度时间轴对齐系统：
            // 解决前奏制作人、作词口白等导致的歌词与译文错位问题（杜绝"牛头不对马嘴"）
            val matchedTrans = MutableList(origLines.size) { "" }
            val usedTransIndices = BooleanArray(transLines.size) { false }

            // Pass 1: 绝对时间戳精准对齐 (diff == 0ms)
            for (i in origLines.indices) {
                val oTime = origTimes[i] ?: continue
                for (j in transLines.indices) {
                    if (!usedTransIndices[j] && transTimes[j] == oTime) {
                        matchedTrans[i] = transLines[j]
                        usedTransIndices[j] = true
                        break
                    }
                }
            }

            // Pass 2: 邻近微时间差吸附 (diff <= 500ms)
            for (i in origLines.indices) {
                if (matchedTrans[i].isNotBlank()) continue
                val oTime = origTimes[i] ?: continue
                var bestDiff = 500L
                var bestJ = -1
                for (j in transLines.indices) {
                    if (!usedTransIndices[j] && transTimes[j] != null) {
                        val diff = kotlin.math.abs(oTime - transTimes[j]!!)
                        if (diff <= bestDiff) {
                            bestDiff = diff
                            bestJ = j
                        }
                    }
                }
                if (bestJ != -1) {
                    matchedTrans[i] = transLines[bestJ]
                    usedTransIndices[bestJ] = true
                }
            }

            for (i in origLines.indices) {
                val orig = origLines[i]
                val time = origTimes[i]
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

        val isExactSizeMatch = origLines.size == transLines.size

        if (isExactSizeMatch) {
            // 行数完全对齐时（如时间戳精准对齐生成的原歌词与译文），按行 1:1 绝对映射，杜绝错位
            for (i in origLines.indices) {
                val orig = origLines[i]
                val trans = transLines[i]
                result.add(
                    BilingualLyricLine(
                        lineNumber = i + 1,
                        original = orig,
                        translation = trans,
                        isStanzaBreak = orig.isBlank() && trans.isBlank(),
                        startTimeMs = origTimes.getOrNull(i)
                    )
                )
            }
        } else {
            // 智能段落容错对齐：
            // 大模型经常会漏掉原歌词中的空行（导致原歌词第 10 行空行与译文第 10 行文字错位，引发后面全部移位“不齐”）。
            // 策略：保留原歌词空行作为段落标记，原歌词的每一句非空歌词严格匹配译文的每一句非空歌词！
            val nonBlankTrans = transLines.filter { it.isNotBlank() }
            var transIdx = 0
            var lineNum = 1

            for (i in origLines.indices) {
                val orig = origLines[i]
                val time = origTimes.getOrNull(i)
                if (orig.isBlank()) {
                    result.add(
                        BilingualLyricLine(
                            lineNumber = lineNum++,
                            original = "",
                            translation = "",
                            isStanzaBreak = true,
                            startTimeMs = time
                        )
                    )
                } else {
                    val trans = if (transIdx < nonBlankTrans.size) nonBlankTrans[transIdx++] else ""
                    result.add(
                        BilingualLyricLine(
                            lineNumber = lineNum++,
                            original = orig,
                            translation = trans,
                            isStanzaBreak = false,
                            startTimeMs = time
                        )
                    )
                }
            }

            while (transIdx < nonBlankTrans.size) {
                result.add(
                    BilingualLyricLine(
                        lineNumber = lineNum++,
                        original = "",
                        translation = nonBlankTrans[transIdx++],
                        isStanzaBreak = false,
                        startTimeMs = null
                    )
                )
            }
        }

        return result
    }

    data class TimedLyric(val ms: Long, val text: String)

    private val TIMESTAMP_PARSER_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?\]""")

    private val CREDIT_FILTER_REGEX = Regex(
        """(作词|作曲|编曲|制作人|监制|混音|母带|录音|吉他|贝斯|鼓|键盘|和音|弦乐|OP|SP|Producer|Writers|Written\s*by|Lyrics\s*by|Composed\s*by|Arranged\s*by|Mixed\s*by|Mastered\s*by|Sample)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 将网易云等平台返回的包含 [mm:ss.xx] 时间戳的原版歌词与翻译歌词，
     * 通过毫秒级时间戳精准匹配并消除制作人名单，输出 1:1 行对齐且保留时间标签的歌词文本。
     */
    fun alignLrcTimestamps(origLrc: String, transLrc: String?): Pair<String, String> {
        val origTimed = parseTimedLines(origLrc)
        val transTimed = if (!transLrc.isNullOrBlank()) parseTimedLines(transLrc) else emptyList()

        // 过滤开头的制作信息行（前 3.5 秒内包含制作人、作词作曲等纯制作信息）
        val filteredOrig = origTimed.filterNot { item ->
            item.ms < 3500 && item.text.contains(CREDIT_FILTER_REGEX)
        }

        if (filteredOrig.isEmpty()) {
            val fallbackClean = origLrc.lines().map { cleanLine(it) }.filter { it.isNotBlank() }.joinToString("\n")
            return Pair(fallbackClean, "")
        }

        if (transTimed.isEmpty()) {
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

            // 查找时间戳最匹配且偏差在 500ms 内的对应译文行
            val matchedTrans = transTimed
                .filter { Math.abs(it.ms - current.ms) <= 500 }
                .minByOrNull { Math.abs(it.ms - current.ms) }
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
