package com.linernotes.app.core.playback

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
