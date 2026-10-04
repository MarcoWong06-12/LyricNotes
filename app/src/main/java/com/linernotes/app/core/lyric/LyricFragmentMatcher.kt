package com.linernotes.app.core.lyric

import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.core.util.HtmlUtils
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import kotlin.math.max

object LyricFragmentMatcher {

    private val PUNCTUATION_REGEX = Regex("""[,\.\?!\-\'\"“”‘’\(\)\[\]{}，。？！、“”‘’…—~：；:;·\*]+""")
    private val SECTION_HEADER_REGEX = Regex("""^\[(?:verse|chorus|hook|bridge|intro|outro|pre-chorus|break|interlude|refrain|produced|part).*?\]$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * 将典故注释列表精确锚定至歌词行。
     * 核心原则：
     * 1. 单行典故精确匹配对应的单条歌词行；
     * 2. 多行段落典故（Passage/Stanza）精准识别其在歌词中出现的连续区间，并【仅锚定在起始行】，
     *    严禁向下重复扩散至后续每一行，彻底杜绝“后行歌词显示前行无关引言/倒错”现象；
     * 3. 单句高精度注释优先于大段落注释，避免具体金句被宽泛段落注释覆盖。
     */
    fun matchAnnotationsToLines(
        lines: List<BilingualLyricLine>,
        annotations: List<LyricAnnotationEntity>
    ): Map<Int, LyricAnnotationEntity> {
        if (lines.isEmpty() || annotations.isEmpty()) return emptyMap()

        val normalizedLines = lines.map { normalizeText(it.original) }

        // 区分单行与多行注释
        val singleLineAnnotations = mutableListOf<LyricAnnotationEntity>()
        val multiLineAnnotations = mutableListOf<Pair<LyricAnnotationEntity, List<String>>>()

        for (annotation in annotations) {
            val frag = annotation.lyricFragment.trim()
            if (frag.isBlank()) continue

            val cleanedLines = frag.lines()
                .map { normalizeText(it) }
                .filter { it.isNotBlank() && !SECTION_HEADER_REGEX.matches(it) }

            when {
                cleanedLines.isEmpty() -> {
                    val single = normalizeText(frag)
                    if (single.isNotBlank() && !SECTION_HEADER_REGEX.matches(single)) {
                        singleLineAnnotations.add(annotation)
                    }
                }
                cleanedLines.size == 1 -> {
                    singleLineAnnotations.add(annotation)
                }
                else -> {
                    multiLineAnnotations.add(Pair(annotation, cleanedLines))
                }
            }
        }

        val result = mutableMapOf<Int, LyricAnnotationEntity>()
        val lineScores = mutableMapOf<Int, Float>()

        // 1. 第一阶段：匹配单行注释（最高精确度与特异性）
        for (annotation in singleLineAnnotations) {
            val normFrag = normalizeText(annotation.lyricFragment)
            if (normFrag.length < 3) continue

            var bestIdx = -1
            var bestScore = 0.45f // 提高匹配容错率，适配不同歌词源之间的标点与括号细微差异

            for (i in normalizedLines.indices) {
                val normLine = normalizedLines[i]
                if (normLine.isBlank()) continue

                val score = calculateLineSimilarity(normLine, normFrag)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }

            if (bestIdx >= 0) {
                val existingScore = lineScores[bestIdx] ?: 0f
                val existing = result[bestIdx]
                if (existing == null || isHigherPriority(annotation, bestScore, existing, existingScore)) {
                    val matchedTrans = lines[bestIdx].translation.takeIf { it.isNotBlank() } ?: annotation.lyricTranslation
                    val enriched = if (matchedTrans != null && annotation.lyricTranslation.isNullOrBlank()) {
                        annotation.copy(lyricTranslation = matchedTrans)
                    } else annotation
                    result[bestIdx] = enriched
                    lineScores[bestIdx] = bestScore
                }
            }
        }

        // 2. 第二阶段：匹配多行段落注释（严控仅锚定在连续区间起始行）
        // 按段落行数升序排序，使更具体的 2~4 行段落优先锚定，超长段落补充未覆盖区间
        multiLineAnnotations.sortBy { it.second.size }

        for ((annotation, fragLines) in multiLineAnnotations) {
            val numFragLines = fragLines.size
            if (numFragLines > normalizedLines.size) continue

            var bestStartIdx = -1
            var bestAvgScore = 0.40f

            for (startIdx in 0..(normalizedLines.size - numFragLines)) {
                var totalScore = 0f
                var validLineMatches = 0

                for (offset in 0 until numFragLines) {
                    val line = normalizedLines[startIdx + offset]
                    val fragLine = fragLines[offset]
                    val sim = calculateLineSimilarity(line, fragLine)
                    totalScore += sim
                    if (sim >= 0.50f) {
                        validLineMatches++
                    }
                }

                val avgScore = totalScore / numFragLines
                val requiredMatches = max(2, (numFragLines * 0.5).toInt())
                if (avgScore > bestAvgScore && validLineMatches >= requiredMatches) {
                    bestAvgScore = avgScore
                    bestStartIdx = startIdx
                }
            }

            if (bestStartIdx >= 0) {
                // 查找该连续区间内最适宜的挂载点（优先起始行；若起始行已有单行精确注释，则顺延至该区间首个空闲且相似度匹配行）
                var anchorIdx = -1
                if (!result.containsKey(bestStartIdx)) {
                    anchorIdx = bestStartIdx
                } else {
                    for (offset in 1 until numFragLines) {
                        val idx = bestStartIdx + offset
                        if (!result.containsKey(idx)) {
                            val sim = calculateLineSimilarity(normalizedLines[idx], fragLines[offset])
                            if (sim >= 0.40f) {
                                anchorIdx = idx
                                break
                            }
                        }
                    }
                }

                if (anchorIdx >= 0 && !result.containsKey(anchorIdx)) {
                    val matchedTrans = lines.subList(bestStartIdx, (bestStartIdx + numFragLines).coerceAtMost(lines.size))
                        .mapNotNull { it.translation.takeIf { t -> t.isNotBlank() } }
                        .joinToString("\n")
                        .takeIf { it.isNotBlank() } ?: lines[anchorIdx].translation.takeIf { it.isNotBlank() } ?: annotation.lyricTranslation

                    val enriched = if (matchedTrans != null && annotation.lyricTranslation.isNullOrBlank()) {
                        annotation.copy(lyricTranslation = matchedTrans)
                    } else annotation

                    result[anchorIdx] = enriched
                    lineScores[anchorIdx] = bestAvgScore
                }
            }
        }

        return result
    }

    /**
     * 计算单行歌词与注释引文的高精度语义相似度 (0.0 ~ 1.0)
     */
    private fun calculateLineSimilarity(line: String, frag: String): Float {
        if (line.isBlank() || frag.isBlank()) return 0f
        if (line == frag) return 1.0f

        // 相互包含：仅当引文具有足够长度（至少 6 字符且包含空格），避免单一常用词误伤
        if (frag.length >= 6 && frag.contains(' ') && line.contains(frag)) {
            val ratio = frag.length.toFloat() / line.length.toFloat()
            return 0.85f + (ratio * 0.14f)
        }
        if (line.length >= 6 && line.contains(' ') && frag.contains(line)) {
            val ratio = line.length.toFloat() / frag.length.toFloat()
            return 0.80f + (ratio * 0.15f)
        }

        // 词元（Token）交集相似度计算 (词袋多重集求交，防止重复词导致召回膨胀)
        val lineWords = line.split(WHITESPACE_REGEX).filter { it.length >= 2 }
        val fragWords = frag.split(WHITESPACE_REGEX).filter { it.length >= 2 }
        if (lineWords.isEmpty() || fragWords.isEmpty()) return 0f

        val lineCounts = mutableMapOf<String, Int>()
        for (w in lineWords) lineCounts[w] = (lineCounts[w] ?: 0) + 1
        val fragCounts = mutableMapOf<String, Int>()
        for (w in fragWords) fragCounts[w] = (fragCounts[w] ?: 0) + 1

        var matchedCount = 0
        for ((w, lCount) in lineCounts) {
            val fCount = fragCounts[w]
                ?: (if (w.endsWith("ing")) fragCounts[w.removeSuffix("ing") + "in"] else null)
                ?: (if (w.endsWith("in")) fragCounts[w.removeSuffix("in") + "ing"] else null)
                ?: 0
            matchedCount += kotlin.math.min(lCount, fCount)
        }

        val recall = matchedCount.toFloat() / lineWords.size.toFloat()
        val precision = matchedCount.toFloat() / fragWords.size.toFloat()
        if (recall + precision <= 0f) return 0f

        val f1 = (2 * recall * precision) / (recall + precision)
        return if (matchedCount >= 2) f1 else 0f
    }

    private fun isHigherPriority(
        candidate: LyricAnnotationEntity,
        candidateScore: Float,
        current: LyricAnnotationEntity,
        currentScore: Float
    ): Boolean {
        if (candidate.isVerified && !current.isVerified) return true
        if (!candidate.isVerified && current.isVerified) return false
        if (candidateScore > currentScore + 0.15f) return true
        if (candidate.votesTotal > current.votesTotal + 10) return true
        return candidateScore > currentScore
    }

    fun unescapeHtml(text: String): String = HtmlUtils.unescapeHtml(text)

    fun normalizeText(text: String): String {
        val unescaped = unescapeHtml(text)
        val simplified = ChineseConverter.toSimplified(unescaped)
        return simplified
            .replace(Regex("""\[.*?\]"""), " ") // 移除 [Verse 1]
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace(PUNCTUATION_REGEX, " ")
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
