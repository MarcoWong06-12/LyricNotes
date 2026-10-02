package com.linernotes.app.core.util

object HtmlUtils {

    private val NUMERIC_ENTITY_REGEX = Regex("""&#(\d+);""")
    private val HEX_ENTITY_REGEX = Regex("""&#x([0-9a-fA-F]+);""")
    private val GENIUS_TAG_REGEX = Regex("""(?:andr|and|or|和|与|及)?\s*<e:\d+>|<e:[^>]+>|<[^>]+>""", RegexOption.IGNORE_CASE)
    private val PLACEHOLDER_REGEX = Regex("""\[(?:embedded\s+content|image:[^\]]+)\]""", RegexOption.IGNORE_CASE)

    /**
     * 统一对 HTML 实体进行反转义处理，覆盖常见命名实体及十进制/十六进制数字实体（支持多层嵌套反转义）
     */
    fun unescapeHtml(text: String): String {
        if (text.isBlank()) return text
        var res = text
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace("</p>", "\n\n")
            .replace("<p>", "")

        var pass = 0
        while (res.contains('&') && pass < 3) {
            val previous = res
            res = res
                .replace("&amp;#39;", "'")
                .replace("&amp;#039;", "'")
                .replace("&amp;quot;", "\"")
                .replace("&amp;amp;", "&")
                .replace("&amp;lt;", "<")
                .replace("&amp;gt;", ">")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .replace("&#x27;", "'")
                .replace("&#x2F;", "/")
                .replace("&apos;", "'")
                .replace("&rsquo;", "'")
                .replace("&lsquo;", "'")
                .replace("&quot;", "\"")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&copy;", "©")
                .replace("&reg;", "®")
                .replace("&trade;", "™")

            if (res.contains("&#")) {
                res = NUMERIC_ENTITY_REGEX.replace(res) { match ->
                    val code = match.groupValues[1].toIntOrNull()
                    if (code != null) {
                        try {
                            code.toChar().toString()
                        } catch (e: Exception) {
                            match.value
                        }
                    } else match.value
                }
                res = HEX_ENTITY_REGEX.replace(res) { match ->
                    val code = match.groupValues[1].toIntOrNull(16)
                    if (code != null) {
                        try {
                            code.toChar().toString()
                        } catch (e: Exception) {
                            match.value
                        }
                    } else match.value
                }
            }
            if (res == previous) break
            pass++
        }
        return res
    }

    private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]+)\]\([^)]+\)""")
    private val INLINE_METADATA_REGEX = Regex("""\[(?:embedded\s+content|image:[^\]]+|Chorus|Verse\s*\d*|Intro|Outro|Bridge|Hook|Pre-Chorus|Post-Chorus)\]""", RegexOption.IGNORE_CASE)

    /**
     * 剥离所有 HTML/XML 标签、Genius 实体标签（如 <e:1>、andr<e:1>）、Markdown 链接及嵌入式占位符
     */
    fun stripTags(text: String): String {
        if (text.isBlank()) return text
        return text
            .replace(PLACEHOLDER_REGEX, "")
            .replace(INLINE_METADATA_REGEX, "")
            .replace(GENIUS_TAG_REGEX, "")
            .replace(MARKDOWN_LINK_REGEX, "$1") // 保留 Markdown 链接中的文字，剥离 URL
            .replace("\u200B", "") // 零宽空格
            .replace("\uFEFF", "") // BOM
            .replace("\u00A0", " ") // 不换行空格
            .replace("\r", "")
    }

    /**
     * 深度清洗纯文本（脱水解码并剥离一切标签与占位符，规整多余空行）
     */
    fun cleanPlainText(text: String): String {
        if (text.isBlank()) return ""
        val unescaped = unescapeHtml(text)
        val stripped = stripTags(unescaped)
        return stripped
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    /**
     * 专门针对翻译引擎输出进行二次净化，清除如 andr<e:1>、残留实体及格式畸变
     */
    fun cleanTranslationOutput(text: String): String {
        if (text.isBlank()) return ""
        return cleanPlainText(text)
            .replace(Regex("""(?:andr|and|or|和|与|及)?\s*<e:\d+>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""^[\s\-·•]+"""), "") // 清除行首无谓的项目符号
            .trim()
    }
}
