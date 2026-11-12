package com.linernotes.app.core.playback

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import com.linernotes.app.core.i18n.AppStrings

enum class MediaSourceApp(val displayName: String, val packageName: String) {
    SPOTIFY("Spotify", "com.spotify.music"),
    APPLE_MUSIC("Apple Music", "com.apple.android.music"),
    NETEASE("网易云音乐", "com.netease.cloudmusic"),
    QQ_MUSIC("QQ音乐", "com.tencent.qqmusic"),
    YOUTUBE_MUSIC("YouTube Music", "com.google.android.apps.youtube.music"),
    UNKNOWN("流媒体播放器", "");

    fun localizedName(strings: AppStrings): String = when (this) {
        NETEASE -> strings.mediaSourceNetease
        QQ_MUSIC -> strings.mediaSourceQq
        UNKNOWN -> strings.mediaSourceUnknown
        else -> displayName
    }

    companion object {
        fun fromPackageName(pkg: String?): MediaSourceApp {
            if (pkg == null) return UNKNOWN
            return entries.find { it.packageName == pkg } ?: UNKNOWN
        }
    }
}

enum class CoverQuality(val rank: Int) {
    NONE(0),
    GENIUS(1),
    NOTIFICATION_LOCAL(2),
    LYRIC_SOURCE(3),
    NATIVE_METADATA(3),
    ITUNES_HD(4)
}

@Immutable
data class QueueTrackItem(
    val id: Long = -1L,
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverUri: String? = null
)

/**
 * 低频静态曲目元数据与播控模式（仅切歌、封面、队列或播放模式变更时发射）
 */
@Immutable
data class TrackMetadataState(
    val trackEpoch: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val packageName: String = "",
    val trackId: String? = null,
    val sourceApp: MediaSourceApp = MediaSourceApp.UNKNOWN,
    val isShuffleActive: Boolean = false,
    val repeatMode: Int = 0,
    val queueItems: List<QueueTrackItem> = emptyList()
) {
    val isSpotify: Boolean
        get() = packageName == "com.spotify.music" || sourceApp == MediaSourceApp.SPOTIFY

    val hasValidTrack: Boolean
        get() = title.isNotBlank() && artist.isNotBlank()

    fun toDisplayPlaybackState(isPlaying: Boolean, positionMs: Long = 0L): TrackPlaybackState =
        TrackPlaybackState(
            trackEpoch = trackEpoch,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            currentPositionMs = positionMs,
            isPlaying = isPlaying,
            playbackSpeed = 1.0f,
            lastUpdateTimeMs = 0L,
            coverUrl = coverUrl,
            packageName = packageName,
            trackId = trackId,
            sourceApp = sourceApp,
            isShuffleActive = isShuffleActive,
            repeatMode = repeatMode,
            queueItems = queueItems
        )
}

/**
 * 轻量播放时钟锚点（仅在切歌、播放/暂停、倍速变化或非平凡 Seek/漂移时发射）
 */
@Immutable
data class PlaybackClockState(
    val trackEpoch: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val lastUpdateTimeMs: Long = 0L,
    val durationMs: Long = 0L
) {
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

/**
 * 流媒体实时播放状态数据载体
 */
@Immutable
data class TrackPlaybackState(
    val trackEpoch: Long = 0L,
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

    fun withClock(clock: PlaybackClockState): TrackPlaybackState =
        if (trackEpoch > 0L && clock.trackEpoch > 0L && clock.trackEpoch != trackEpoch) {
            this
        } else {
            copy(
                currentPositionMs = clock.currentPositionMs,
                isPlaying = clock.isPlaying,
                playbackSpeed = clock.playbackSpeed,
                lastUpdateTimeMs = clock.lastUpdateTimeMs,
                durationMs = if (clock.durationMs > 0L) clock.durationMs else durationMs
            )
        }

    fun toMetadata(): TrackMetadataState = TrackMetadataState(
        trackEpoch = trackEpoch,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        coverUrl = coverUrl,
        packageName = packageName,
        trackId = trackId,
        sourceApp = sourceApp,
        isShuffleActive = isShuffleActive,
        repeatMode = repeatMode,
        queueItems = queueItems
    )

    fun toClock(): PlaybackClockState = PlaybackClockState(
        trackEpoch = trackEpoch,
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed,
        lastUpdateTimeMs = lastUpdateTimeMs,
        durationMs = durationMs
    )

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
