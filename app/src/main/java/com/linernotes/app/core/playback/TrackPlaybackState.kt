package com.linernotes.app.core.playback

import android.os.SystemClock
import androidx.compose.runtime.Immutable

enum class MediaSourceApp(val displayName: String, val packageName: String) {
    SPOTIFY("Spotify", "com.spotify.music"),
    APPLE_MUSIC("Apple Music", "com.apple.android.music"),
    NETEASE("网易云音乐", "com.netease.cloudmusic"),
    QQ_MUSIC("QQ音乐", "com.tencent.qqmusic"),
    YOUTUBE_MUSIC("YouTube Music", "com.google.android.apps.youtube.music"),
    UNKNOWN("流媒体播放器", "");

    companion object {
        fun fromPackageName(pkg: String?): MediaSourceApp {
            if (pkg == null) return UNKNOWN
            return entries.find { it.packageName == pkg } ?: UNKNOWN
        }
    }
}

@Immutable
data class QueueTrackItem(
    val id: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverUri: String? = null
)

/**
 * 流媒体实时播放状态数据载体
 */
@Immutable
data class TrackPlaybackState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val lastUpdateTimeMs: Long = 0L,
    val coverUrl: String? = null,
    val packageName: String = "",
    val trackId: String? = null,
    val sourceApp: MediaSourceApp = MediaSourceApp.UNKNOWN,
    val isShuffleActive: Boolean = false,
    val repeatMode: Int = 0, // 0: OFF, 1: ALL (列表循环), 2: ONE (单曲循环)
    val queueItems: List<QueueTrackItem> = emptyList()
) {
    val isSpotify: Boolean
        get() = packageName == "com.spotify.music" || sourceApp == MediaSourceApp.SPOTIFY

    val hasValidTrack: Boolean
        get() = title.isNotBlank() && artist.isNotBlank()

    /**
     * 根据系统高精度时钟插值推算当前播放毫秒位置 (防跳帧与时间漂移)
     */
    fun getEstimatedPositionMs(): Long {
        if (!isPlaying || lastUpdateTimeMs <= 0L) {
            return currentPositionMs.coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
        }
        val elapsed = SystemClock.elapsedRealtime() - lastUpdateTimeMs
        val estimated = currentPositionMs + (elapsed * playbackSpeed).toLong()
        return if (durationMs > 0) {
            estimated.coerceIn(0L, durationMs)
        } else {
            estimated.coerceAtLeast(0L)
        }
    }
}
