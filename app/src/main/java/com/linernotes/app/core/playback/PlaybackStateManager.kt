package com.linernotes.app.core.playback

import android.content.Context
import android.os.SystemClock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

interface MediaControlActionHandler {
    fun play()
    fun pause()
    fun togglePlayPause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeatMode()
    fun skipToQueueItem(item: QueueTrackItem)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackStateManagerEntryPoint {
    fun playbackStateManager(): PlaybackStateManager
}

/**
 * 全局播放状态分发中心与播控调度器
 */
@Singleton
class PlaybackStateManager @Inject constructor() {

    private val stateLock = Any()
    private var trackEpochCounter: Long = 0L
    private var currentCoverRank: Int = CoverQuality.NONE.rank

    private val _playbackState = MutableStateFlow(TrackPlaybackState())
    val playbackState: StateFlow<TrackPlaybackState> = _playbackState.asStateFlow()

    private val _trackMetadata = MutableStateFlow(TrackMetadataState())
    val trackMetadata: StateFlow<TrackMetadataState> = _trackMetadata.asStateFlow()

    private val _playbackClock = MutableStateFlow(PlaybackClockState())
    val playbackClock: StateFlow<PlaybackClockState> = _playbackClock.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    @Volatile
    private var controlActionHandler: MediaControlActionHandler? = null

    init {
        instance = this
    }

    fun refreshNotificationAccess(context: Context) {
        _notificationAccessGranted.value = MediaPlaybackSyncService.isNotificationAccessGranted(context)
    }

    fun setNotificationAccessGranted(granted: Boolean) {
        _notificationAccessGranted.value = granted
    }

    fun updateState(newState: TrackPlaybackState) {
        synchronized(stateLock) {
            val current = _playbackState.value
            if (!newState.hasValidTrack && current.hasValidTrack && newState.isPlaying) {
                return
            }

            val isTrackChanged = newState.title != current.title || newState.artist != current.artist
            if (isTrackChanged) {
                trackEpochCounter++
                currentCoverRank = if (!newState.coverUrl.isNullOrBlank()) {
                    CoverQuality.NATIVE_METADATA.rank
                } else {
                    CoverQuality.NONE.rank
                }
                val stamped = newState.copy(trackEpoch = trackEpochCounter)
                _playbackState.value = stamped
                _trackMetadata.value = stamped.toMetadata()
                _playbackClock.value = stamped.toClock()
                return
            }

            val epoch = current.trackEpoch.takeIf { it > 0L } ?: run {
                if (newState.hasValidTrack) ++trackEpochCounter else 0L
            }

            val isMetadataSame =
                newState.album == current.album &&
                    newState.durationMs == current.durationMs &&
                    newState.coverUrl == current.coverUrl &&
                    newState.packageName == current.packageName &&
                    newState.trackId == current.trackId &&
                    newState.sourceApp == current.sourceApp &&
                    newState.isShuffleActive == current.isShuffleActive &&
                    newState.repeatMode == current.repeatMode &&
                    newState.queueItems == current.queueItems

            val isClockModeSame =
                newState.isPlaying == current.isPlaying &&
                    newState.playbackSpeed == current.playbackSpeed &&
                    newState.durationMs == current.durationMs

            // 心跳漂移容差去重：防止相同播放锚点因 elapsedRealtime() 递增触发 StateFlow 全量刷新
            val isRedundantClockTick = if (isClockModeSame) {
                if (newState.isPlaying && current.lastUpdateTimeMs > 0L && newState.lastUpdateTimeMs > 0L) {
                    val drift = abs(current.getEstimatedPositionMs() - newState.currentPositionMs)
                    drift < CLOCK_DRIFT_TOLERANCE_MS
                } else {
                    newState.currentPositionMs == current.currentPositionMs
                }
            } else {
                false
            }

            if (isMetadataSame && isRedundantClockTick) {
                return
            }

            val stamped = if (isRedundantClockTick) {
                newState.copy(
                    trackEpoch = epoch,
                    currentPositionMs = current.currentPositionMs,
                    lastUpdateTimeMs = current.lastUpdateTimeMs
                )
            } else {
                newState.copy(trackEpoch = epoch)
            }

            _playbackState.value = stamped
            if (!isMetadataSame) {
                _trackMetadata.value = stamped.toMetadata()
            }
            if (!isRedundantClockTick) {
                _playbackClock.value = stamped.toClock()
            }
        }
    }

    fun updatePositionOnly(positionMs: Long, isPlaying: Boolean) {
        synchronized(stateLock) {
            val current = _playbackState.value
            if (!current.hasValidTrack) return

            val isSamePlaying = current.isPlaying == isPlaying
            val drift = abs(current.getEstimatedPositionMs() - positionMs)
            if (isSamePlaying && current.lastUpdateTimeMs > 0L) {
                if ((isPlaying && drift < CLOCK_DRIFT_TOLERANCE_MS) ||
                    (!isPlaying && positionMs == current.currentPositionMs)
                ) {
                    return
                }
            }

            val updated = current.copy(
                currentPositionMs = positionMs,
                isPlaying = isPlaying,
                lastUpdateTimeMs = SystemClock.elapsedRealtime()
            )
            _playbackState.value = updated
            _playbackClock.value = updated.toClock()
        }
    }

    /**
     * 带曲目校验与画质优先级的封面回填仲裁，防止切歌竞态与低清圖覆蓋高清圖
     */
    fun updateCoverIfBetter(
        expectedTitle: String,
        expectedArtist: String,
        coverUrl: String,
        quality: CoverQuality,
        expectedEpoch: Long = -1L
    ): Boolean {
        if (coverUrl.isBlank()) return false
        synchronized(stateLock) {
            val current = _playbackState.value
            if (current.title != expectedTitle || current.artist != expectedArtist) return false
            if (expectedEpoch > 0L && current.trackEpoch != expectedEpoch) return false

            val existing = current.coverUrl
            val shouldAccept = when {
                existing.isNullOrBlank() -> true
                quality.rank > currentCoverRank -> true
                existing.startsWith("/") && quality.rank >= CoverQuality.LYRIC_SOURCE.rank -> true
                else -> false
            }
            if (!shouldAccept || existing == coverUrl) return false

            currentCoverRank = quality.rank
            val updated = current.copy(coverUrl = coverUrl)
            _playbackState.value = updated
            _trackMetadata.value = updated.toMetadata()
            return true
        }
    }

    fun registerControlHandler(handler: MediaControlActionHandler) {
        this.controlActionHandler = handler
    }

    fun unregisterControlHandler(handler: MediaControlActionHandler) {
        if (this.controlActionHandler == handler) {
            this.controlActionHandler = null
        }
    }

    fun play() = controlActionHandler?.play()
    fun pause() = controlActionHandler?.pause()
    fun togglePlayPause() = controlActionHandler?.togglePlayPause()
    fun skipToNext() = controlActionHandler?.skipToNext()
    fun skipToPrevious() = controlActionHandler?.skipToPrevious()
    fun seekTo(positionMs: Long) = controlActionHandler?.seekTo(positionMs)
    fun toggleShuffle() = controlActionHandler?.toggleShuffle()
    fun cycleRepeatMode() = controlActionHandler?.cycleRepeatMode()
    fun skipToQueueItem(item: QueueTrackItem) = controlActionHandler?.skipToQueueItem(item)

    companion object {
        private const val CLOCK_DRIFT_TOLERANCE_MS = 250L

        @Volatile
        private var instance: PlaybackStateManager? = null

        /**
         * 优先从 Hilt 全局容器获取统一单例，彻底杜绝 Service 与 UI 出现双重实例裂脑
         */
        fun get(context: Context): PlaybackStateManager {
            return instance ?: synchronized(this) {
                instance ?: try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        PlaybackStateManagerEntryPoint::class.java
                    )
                    entryPoint.playbackStateManager().also { instance = it }
                } catch (e: Exception) {
                    instance ?: PlaybackStateManager().also { instance = it }
                }
            }
        }

        fun getInstance(): PlaybackStateManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStateManager().also { instance = it }
            }
        }
    }
}
