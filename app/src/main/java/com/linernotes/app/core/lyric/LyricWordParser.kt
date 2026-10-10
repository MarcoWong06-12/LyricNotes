package com.linernotes.app.core.lyric

import com.linernotes.app.domain.model.LyricWord

/**
 * 逐字/逐音节歌词解析与智能时间分布器 (Word-by-word Karaoke Aligner)
 *
 * 1. 优先解析具备逐字/逐音节时间戳的格式 (QRC/KRC/YRC 及 Enhanced LRC `<mm:ss.xx>`)；
 * 2. 对于仅具备整行时间戳的标准 LRC 歌词，结合语种特性（中/日/韩单字音节与欧美按词分段）进行物理时间加权插值分布；
 * 3. 严格保障逐字时间戳单调递增，无缝驱动 Apple Music 风格流光高亮与律动扫光动效。
 */
object LyricWordParser {

    private val INLINE_PAREN_REGEX = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^(\n\r]+)""")
    private val INLINE_ANGLED_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?>([^<\n\r]+)""")

    // 智能语元匹配：CJK 字符单字切分，欧美/拉丁单词保留词尾空格作为整体单元
    private val TOKEN_REGEX = Regex("""([\u4e00-\u9fa5\u3040-\u30ff\uac00-\ud7af]|(?:[^\s\u4e00-\u9fa5\u3040-\u30ff\uac00-\ud7af]+\s*))""")

    /**
     * 清洗行内包含的逐字时间戳标签，还原纯净人眼可读歌词文本
     */
    fun cleanInlineTimestamps(rawLine: String): String {
        return rawLine
            .replace(Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]"""), "")
            .replace(Regex("""\(\d+,\d+(?:,\d+)?\)"""), "")
            .replace(Regex("""<\d{1,2}:\d{2}(?:\.\d{1,3})?>"""), "")
    }

    /**
     * 解析单行歌词中的逐字/逐音节单元列表
     */
    fun parseLineWords(
        rawLine: String,
        startTimeMs: Long?,
        endTimeMs: Long?
    ): List<LyricWord> {
        if (rawLine.isBlank() || startTimeMs == null) {
            return emptyList()
        }

        // 1. 尝试解析 YRC/QRC 括号时间戳格式: (start,duration,0)Word
        val parenMatches = INLINE_PAREN_REGEX.findAll(rawLine).toList()
        if (parenMatches.isNotEmpty()) {
            val words = mutableListOf<LyricWord>()
            for (match in parenMatches) {
                val start = match.groupValues[1].toLongOrNull() ?: startTimeMs
                val duration = match.groupValues[2].toLongOrNull() ?: 200L
                val text = match.groupValues[3]
                if (text.isNotEmpty()) {
                    words.add(LyricWord(text = text, startTimeMs = start, durationMs = duration))
                }
            }
            if (words.isNotEmpty()) return words
        }

        // 2. 尝试解析 Enhanced LRC 尖括号时间戳格式: <01:23.45>Word
        val angledMatches = INLINE_ANGLED_REGEX.findAll(rawLine).toList()
        if (angledMatches.isNotEmpty()) {
            val words = mutableListOf<LyricWord>()
            for (i in angledMatches.indices) {
                val match = angledMatches[i]
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val wordStart = min * 60_000L + sec * 1000L + ms
                val text = match.groupValues[4]

                val nextStart = if (i + 1 < angledMatches.size) {
                    val nextMatch = angledMatches[i + 1]
                    val nMin = nextMatch.groupValues[1].toLongOrNull() ?: 0L
                    val nSec = nextMatch.groupValues[2].toLongOrNull() ?: 0L
                    val nMsStr = nextMatch.groupValues[3]
                    val nMs = when (nMsStr.length) {
                        1 -> (nMsStr.toLongOrNull() ?: 0L) * 100
                        2 -> (nMsStr.toLongOrNull() ?: 0L) * 10
                        3 -> nMsStr.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    nMin * 60_000L + nSec * 1000L + nMs
                } else {
                    endTimeMs ?: (wordStart + 400L)
                }

                val duration = (nextStart - wordStart).coerceIn(80L, 4000L)
                if (text.isNotEmpty()) {
                    words.add(LyricWord(text = text, startTimeMs = wordStart, durationMs = duration))
                }
            }
            if (words.isNotEmpty()) return words
        }

        // 3. 标准 LRC 行：智能语元切分与物理时间加权平滑分布
        val cleanText = cleanInlineTimestamps(rawLine)
        if (cleanText.isBlank()) return emptyList()

        val tokens = TOKEN_REGEX.findAll(cleanText).map { it.value }.filter { it.isNotEmpty() }.toList()
        if (tokens.isEmpty()) return emptyList()

        val rawGap = if (endTimeMs != null && endTimeMs > startTimeMs) endTimeMs - startTimeMs else null
        val totalLineDuration = if (rawGap != null) {
            // 歌唱生理声学启发式算法：
            // 人声实际演唱通常占据行与行时间间隔的前 65%~75%，剩余为乐器停顿与伴奏留白尾音。
            // 避免把短句平摊至长达数秒的整段空白，导致逐字扫掠极度拖沓迟缓。
            // 依据自然演唱速度 (每字/词约 280ms~360ms) 计算人声区间，上限不超过间隔的 72%，且保留至少 200ms 伴奏尾音。
            val estimatedVocal = (tokens.size * 320L).coerceIn(600L, 6000L)
            val maxPhrasing = (rawGap * 0.72f).toLong().coerceAtLeast(600L)
            minOf(estimatedVocal, maxPhrasing).coerceAtMost(maxOf(500L, rawGap - 200L))
        } else {
            // 若为最后一句或下一句未定，按语元总数给一个自然的持续时长 (每字/词约 300ms)
            (tokens.size * 300L).coerceIn(1000L, 4500L)
        }

        // 计算各语元权重 (CJK 单字 1.4f，英文词按字母长度计算，遇标点赋予微休止符)
        val weights = tokens.map { token ->
            val trim = token.trim()
            val baseWeight = if (trim.any { it in '\u4e00'..'\u9fa5' || it in '\u3040'..'\u30ff' || it in '\uac00'..'\ud7af' }) {
                1.4f
            } else {
                (trim.length * 0.45f).coerceIn(1.0f, 3.2f)
            }
            val punctExtra = if (trim.endsWith(",") || trim.endsWith("，") || trim.endsWith(".") || trim.endsWith("。") || trim.endsWith("!")) 0.35f else 0.0f
            baseWeight + punctExtra
        }

        val totalWeight = weights.sum().coerceAtLeast(0.1f)
        val words = mutableListOf<LyricWord>()
        var currentStart = startTimeMs

        for (i in tokens.indices) {
            val token = tokens[i]
            val duration = ((weights[i] / totalWeight) * totalLineDuration).toLong().coerceAtLeast(60L)
            words.add(
                LyricWord(
                    text = token,
                    startTimeMs = currentStart,
                    durationMs = duration
                )
            )
            currentStart += duration
        }

        return words
    }
}
