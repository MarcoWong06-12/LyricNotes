package com.linernotes.app.core.media

/**
 * 传输给系统媒体通知栏的数据载体
 */
data class MediaPlaybackNotificationData(
    val albumId: String = "",
    val trackTitle: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeLyricSnippet: String? = null,
    val activeAnnotationSnippet: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false
)
