package com.linernotes.app.core.translation

/**
 * 专辑批量后台翻译状态模型
 */
data class BatchTranslationState(
    val isTranslating: Boolean = false,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackIndex: Int = 0,
    val totalTracks: Int = 0,
    val userMessage: String? = null
)
