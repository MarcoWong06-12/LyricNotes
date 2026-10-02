package com.linernotes.app.core.util

object HtmlUtils {

    private val NUMERIC_ENTITY_REGEX = Regex("""&#(\d+);""")
    private val HEX_ENTITY_REGEX = Regex("""&#x([0-9a-fA-F]+);""")

    /**
     * 统一对 HTML 实体进行反转义处理，覆盖常见命名实体及十进制/十六进制数字实体
     */
    fun unescapeHtml(text: String): String {
        if (text.isBlank()) return text
        var res = text
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace("</p>", "\n")
            .replace("<p>", "")
            .replace("&amp;#39;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
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

        if (res.contains("&#")) {
            res = NUMERIC_ENTITY_REGEX.replace(res) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) code.toChar().toString() else match.value
            }
            res = HEX_ENTITY_REGEX.replace(res) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) code.toChar().toString() else match.value
            }
        }
        return res
    }
}
