package com.linernotes.app.domain.model

data class BilingualLyricLine(
    val lineNumber: Int,
    val original: String,
    val translation: String,
    val isStanzaBreak: Boolean = false,
    val startTimeMs: Long? = null
)

enum class LyricDisplayMode {
    BILINGUAL,
    ORIGINAL_ONLY,
    TRANSLATED_ONLY
}
