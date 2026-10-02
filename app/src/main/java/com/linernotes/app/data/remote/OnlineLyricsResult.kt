package com.linernotes.app.data.remote

data class OnlineLyricsResult(
    val songId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val originalLyrics: String = "",
    val translatedLyrics: String? = null,
    val isBilingual: Boolean = false,
    val coverUrl: String? = null,
    val durationMs: Long = 0L
)
