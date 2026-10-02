package com.linernotes.app.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface MediaControlActionHandler {
    fun play()
    fun pause()
    fun togglePlayPause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeatMode()
}

/**
 * 全局播放状态分发中心与播控调度器
 */
@Singleton
class PlaybackStateManager @Inject constructor() {

    private val _playbackState = MutableStateFlow(TrackPlaybackState())
    val playbackState: StateFlow<TrackPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var controlActionHandler: MediaControlActionHandler? = null

    init {
        instance = this
    }

    fun updateState(newState: TrackPlaybackState) {
        val current = _playbackState.value
        // 如果新状态信息有效或者歌曲变更，触发更新
        if (newState.hasValidTrack || !current.hasValidTrack || !newState.isPlaying) {
            _playbackState.value = newState
        }
    }

    fun updatePositionOnly(positionMs: Long, isPlaying: Boolean) {
        val current = _playbackState.value
        if (current.hasValidTrack) {
            _playbackState.value = current.copy(
                currentPositionMs = positionMs,
                isPlaying = isPlaying,
                lastUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
            )
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

    companion object {
        @Volatile
        private var instance: PlaybackStateManager? = null

        fun getInstance(): PlaybackStateManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStateManager().also { instance = it }
            }
        }
    }
}
