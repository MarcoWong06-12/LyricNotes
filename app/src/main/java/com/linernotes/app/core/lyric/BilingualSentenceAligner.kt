package com.linernotes.app.core.lyric

/**
 * 结构化的单句中英对照单元：
 * 中文在上 (主阅读字号)，英文原文在下 (紧凑小字号，不占过多空间)
 */
data class AlignedSentenceUnit(
    val chinese: String,
    val original: String
)

/**
 * 结构化的段落对照单元，包含该段内切分出的全部句子配对
 */
data class AlignedBilingualParagraph(
    val units: List<AlignedSentenceUnit>
)

object BilingualSentenceAligner {

    private val ABBREVIATIONS = listOf(
        "Dr.", "Mr.", "Mrs.", "Ms.", "Prof.", "vs.", "feat.", "ft.",
        "e.g.", "i.e.", "St.", "Jr.", "Sr.", "No.", "Vol.", "approx."
    )

    private val CHINESE_CHAR_REGEX = Regex("""[\u4e00-\u9fa5]""")

    /**
     * 将原文与译文进行段落和句子级的精细对齐。
     * 若未提供译文或原文已为中文，则返回单语言单元。
     */
    fun align(original: String, translation: String?): List<AlignedBilingualParagraph> {
        val origParas = splitParagraphs(original)
        if (origParas.isEmpty()) return emptyList()

        if (translation.isNullOrBlank()) {
            return origParas.map { para ->
                AlignedBilingualParagraph(listOf(AlignedSentenceUnit(chinese = "", original = para)))
            }
        }

        val transParas = splitParagraphs(translation)

        // 1. 段落数一致时，逐段对齐
        if (origParas.size == transParas.size) {
            return origParas.zip(transParas) { oPara, tPara ->
                alignSingleParagraph(oPara, tPara)
            }
        }

        // 2. 原文仅 1 段而译文被切分成多段时，合并译文段落对齐
        if (origParas.size == 1) {
            return listOf(alignSingleParagraph(origParas[0], transParas.joinToString(" ")))
        }

        // 3. 译文按单换行切分后行数与原文段落数吻合，尝试按单行对齐
        val transLines = translation.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (origParas.size == transLines.size) {
            return origParas.zip(transLines) { oPara, tLine ->
                alignSingleParagraph(oPara, tLine)
            }
        }

        // 4. 兜底策略：按段落索引配对
        return origParas.mapIndexed { idx, oPara ->
            val tPara = transParas.getOrNull(idx) ?: ""
            alignSingleParagraph(oPara, tPara)
        }
    }

    /**
     * 对单个段落进行句子级智能拆解与配对
     */
    fun alignSingleParagraph(originalPara: String, translationPara: String): AlignedBilingualParagraph {
        val origClean = originalPara.trim()
        val transClean = translationPara.trim()

        if (transClean.isBlank() || !CHINESE_CHAR_REGEX.containsMatchIn(transClean)) {
            return AlignedBilingualParagraph(listOf(AlignedSentenceUnit(chinese = "", original = origClean)))
        }

        val origSentences = splitEnSentences(origClean)
        val transSentences = splitZhSentences(transClean)

        // 若句子数量严格一致，且大于 1 句，进行 1:1 逐句配对
        if (origSentences.size == transSentences.size && origSentences.isNotEmpty()) {
            val units = transSentences.zip(origSentences) { zh, en ->
                AlignedSentenceUnit(chinese = zh, original = en)
            }
            return AlignedBilingualParagraph(units)
        }

        // 若句子数量不一致（例如 2 句英文被翻译融合成 1 句中文，或标点风格差异），
        // 保持该段整体并排：整段中文在上方，整段英文紧随其下（紧凑小字号）
        // 既保障阅读连贯，又绝不错位误读
        return AlignedBilingualParagraph(
            listOf(AlignedSentenceUnit(chinese = transClean, original = origClean))
        )
    }

    /**
     * 按段落（空行 \n\s*\n）拆分文本
     */
    fun splitParagraphs(text: String): List<String> {
        return text.split(Regex("""(?:\r?\n\s*){2,}"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * 英文句子拆分器（保护常见缩写 Dr./Mr. 以及带引号的句子）
     */
    fun splitEnSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        var protectedText = trimmed
        val placeholderMap = mutableMapOf<String, String>()
        for ((idx, abb) in ABBREVIATIONS.withIndex()) {
            if (protectedText.contains(abb, ignoreCase = true)) {
                val placeholder = "__ABBR_${idx}__"
                placeholderMap[placeholder] = abb
                protectedText = protectedText.replace(Regex(Regex.escape(abb), RegexOption.IGNORE_CASE), placeholder)
            }
        }

        // 匹配标点 (. ! ?) 后紧跟空白和首字母大写/数字/引号的位置
        val pattern = Regex("""(?<=[.!?]["'”’]?)\s+(?=[A-Z0-9"“])""")
        val rawParts = protectedText.split(pattern).map { it.trim() }.filter { it.isNotBlank() }

        return rawParts.map { part ->
            var restored = part
            for ((ph, abb) in placeholderMap) {
                restored = restored.replace(ph, abb)
            }
            restored
        }
    }

    /**
     * 中文句子拆分器（按句号、感叹号、问号拆分并保留标点）
     */
    fun splitZhSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        val pattern = Regex("""(?<=[。！？\n])\s*""")
        return trimmed.split(pattern).map { it.trim() }.filter { it.isNotBlank() }
    }
}
