package com.linernotes.app.data.remote

data class OnlineLyricsResult(
    val songId: Long,
    val title: String,
    val artist: String,
    val originalLyrics: String,
    val translatedLyrics: String?,
    val isBilingual: Boolean
)
