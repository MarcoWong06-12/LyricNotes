package com.linernotes.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class LyricWord(
    val text: String,
    val startTimeMs: Long,
    val durationMs: Long
)

@Immutable
data class BilingualLyricLine(
    val lineNumber: Int,
    val original: String,
    val translation: String,
    val isStanzaBreak: Boolean = false,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val words: List<LyricWord> = emptyList()
)

enum class LyricDisplayMode {
    BILINGUAL,
    ORIGINAL_ONLY,
    TRANSLATED_ONLY
}
