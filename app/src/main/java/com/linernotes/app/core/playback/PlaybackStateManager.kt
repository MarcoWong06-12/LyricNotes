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
    private var elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }

    internal constructor(elapsedRealtimeProvider: () -> Long) : this() {
        this.elapsedRealtimeProvider = elapsedRealtimeProvider
    }

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
            applyStateLocked(_playbackState.value, newState)
        }
    }

    /**
     * 在 stateLock 保护下原子执行 read-modify-write 状态变换，避免并发覆盖封面、队列或 epoch
     */
    fun updatePlaybackStateAtomically(
        transform: (TrackPlaybackState) -> TrackPlaybackState
    ): TrackPlaybackState {
        synchronized(stateLock) {
            val current = _playbackState.value
            val proposed = transform(current)
            if (proposed == current) return current
            return applyStateLocked(current, proposed)
        }
    }

    /**
     * 原子更新播放时钟（位置、播放态、倍速与时间锚点），保留当前曲目元数据与封面
     */
    fun updatePlaybackClock(
        positionMs: Long?,
        isPlaying: Boolean,
        playbackSpeed: Float? = null,
        lastUpdateTimeMs: Long = elapsedRealtimeProvider(),
        requireValidTrack: Boolean = false,
        precondition: ((TrackPlaybackState) -> Boolean)? = null
    ): TrackPlaybackState {
        synchronized(stateLock) {
            val current = _playbackState.value
            if (precondition != null && !precondition(current)) {
                return current
            }
            if (requireValidTrack && !current.hasValidTrack) {
                return current
            }

            val nowMs = elapsedRealtimeProvider()
            val resolvedPos = (positionMs ?: current.currentPositionMs).coerceAtLeast(0L)
            val resolvedSpeed = playbackSpeed?.takeIf { it > 0f } ?: current.playbackSpeed
            val resolvedAnchor = if (lastUpdateTimeMs > 0L) lastUpdateTimeMs else nowMs

            val isClockModeSame =
                isPlaying == current.isPlaying &&
                    resolvedSpeed == current.playbackSpeed

            val isRedundantClockTick = if (isClockModeSame && current.lastUpdateTimeMs > 0L) {
                if (isPlaying) {
                    val candidate = current.copy(
                        currentPositionMs = resolvedPos,
                        isPlaying = isPlaying,
                        playbackSpeed = resolvedSpeed,
                        lastUpdateTimeMs = resolvedAnchor
                    )
                    val drift = abs(current.getEstimatedPositionMs(nowMs) - candidate.getEstimatedPositionMs(nowMs))
                    drift < CLOCK_DRIFT_TOLERANCE_MS
                } else {
                    resolvedPos == current.currentPositionMs
                }
            } else {
                false
            }

            if (isRedundantClockTick) {
                return current
            }

            val updated = current.copy(
                currentPositionMs = resolvedPos,
                isPlaying = isPlaying,
                playbackSpeed = resolvedSpeed,
                lastUpdateTimeMs = resolvedAnchor
            )
            _playbackState.value = updated
            _playbackClock.value = updated.toClock()
            return updated
        }
    }

    /**
     * 原子更新曲目元数据与可选封面，切歌时递增 trackEpoch 并重置位置，同歌时保留更高画质封面与当前时钟
     */
    fun updateMetadataAndCover(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        coverUrl: String? = null,
        coverQuality: CoverQuality = CoverQuality.NATIVE_METADATA,
        packageName: String = "",
        trackId: String? = null,
        sourceApp: MediaSourceApp = MediaSourceApp.fromPackageName(packageName),
        existingCoverValidator: (String) -> Boolean = { true },
        precondition: ((TrackPlaybackState) -> Boolean)? = null
    ): TrackPlaybackState {
        synchronized(stateLock) {
            val current = _playbackState.value
            if (precondition != null && !precondition(current)) {
                return current
            }
            if (title.isBlank()) {
                return current
            }

            val isSameTrack = title == current.title && artist == current.artist
            val resolvedAlbum = if (isSameTrack && album.isBlank()) current.album else album
            val resolvedDuration = if (durationMs > 0L) durationMs else if (isSameTrack) current.durationMs else 0L
            val resolvedTrackId = trackId ?: if (isSameTrack) current.trackId else null
            val resolvedPkg = if (isSameTrack && packageName.isBlank()) current.packageName else packageName
            val resolvedSource = if (isSameTrack && packageName.isBlank() && sourceApp == MediaSourceApp.UNKNOWN) {
                current.sourceApp
            } else {
                sourceApp
            }

            val existingCover = current.coverUrl
            val hasExistingValidCover = isSameTrack &&
                !existingCover.isNullOrBlank() &&
                existingCoverValidator(existingCover)

            val candidateCover = coverUrl?.takeIf { it.isNotBlank() }
            val resolvedCover: String?
            val nextCoverRank: Int
            if (!isSameTrack) {
                resolvedCover = candidateCover
                nextCoverRank = if (candidateCover != null) coverQuality.rank else CoverQuality.NONE.rank
            } else if (candidateCover == null) {
                resolvedCover = if (hasExistingValidCover) existingCover else null
                nextCoverRank = if (hasExistingValidCover) currentCoverRank else CoverQuality.NONE.rank
            } else if (!hasExistingValidCover) {
                resolvedCover = candidateCover
                nextCoverRank = coverQuality.rank
            } else if (coverQuality.rank > currentCoverRank ||
                (existingCover!!.startsWith("/") && coverQuality.rank >= CoverQuality.LYRIC_SOURCE.rank)
            ) {
                resolvedCover = candidateCover
                nextCoverRank = coverQuality.rank
            } else {
                resolvedCover = existingCover
                nextCoverRank = currentCoverRank
            }

            if (!isSameTrack) {
                trackEpochCounter++
                currentCoverRank = nextCoverRank
                val stamped = current.copy(
                    trackEpoch = trackEpochCounter,
                    title = title,
                    artist = artist,
                    album = resolvedAlbum,
                    durationMs = resolvedDuration,
                    currentPositionMs = 0L,
                    lastUpdateTimeMs = elapsedRealtimeProvider(),
                    coverUrl = resolvedCover,
                    packageName = resolvedPkg,
                    trackId = resolvedTrackId,
                    sourceApp = resolvedSource
                )
                _playbackState.value = stamped
                _trackMetadata.value = stamped.toMetadata()
                _playbackClock.value = stamped.toClock()
                return stamped
            }

            val epoch = current.trackEpoch.takeIf { it > 0L } ?: run {
                if (title.isNotBlank() && artist.isNotBlank()) ++trackEpochCounter else 0L
            }
            currentCoverRank = nextCoverRank

            val stamped = current.copy(
                trackEpoch = epoch,
                album = resolvedAlbum,
                durationMs = resolvedDuration,
                coverUrl = resolvedCover,
                packageName = resolvedPkg,
                trackId = resolvedTrackId,
                sourceApp = resolvedSource
            )
            if (stamped == current) {
                return current
            }
            _playbackState.value = stamped
            _trackMetadata.value = stamped.toMetadata()
            if (stamped.durationMs != current.durationMs || stamped.trackEpoch != current.trackEpoch) {
                _playbackClock.value = stamped.toClock()
            }
            return stamped
        }
    }

    fun canAcceptCover(
        expectedTitle: String,
        expectedArtist: String,
        quality: CoverQuality,
        expectedEpoch: Long = -1L,
        existingCoverValidator: (String) -> Boolean = { true }
    ): Boolean {
        synchronized(stateLock) {
            val current = _playbackState.value
            if (current.title != expectedTitle || current.artist != expectedArtist) return false
            if (expectedEpoch > 0L && current.trackEpoch != expectedEpoch) return false
            val existing = current.coverUrl
            return when {
                existing.isNullOrBlank() || !existingCoverValidator(existing) -> true
                quality.rank > currentCoverRank -> true
                existing.startsWith("/") && quality.rank >= CoverQuality.LYRIC_SOURCE.rank -> true
                else -> false
            }
        }
    }

    private fun applyStateLocked(
        current: TrackPlaybackState,
        newState: TrackPlaybackState
    ): TrackPlaybackState {
        if (!newState.hasValidTrack && current.hasValidTrack && newState.isPlaying) {
            return current
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
            return stamped
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
                newState.queueItems == current.queueItems &&
                epoch == current.trackEpoch

        val isClockModeSame =
            newState.isPlaying == current.isPlaying &&
                newState.playbackSpeed == current.playbackSpeed &&
                newState.durationMs == current.durationMs

        val nowMs = elapsedRealtimeProvider()
        // 心跳漂移容差去重：防止相同播放锚点因 elapsedRealtime() 递增触发 StateFlow 全量刷新
        val isRedundantClockTick = if (isClockModeSame) {
            if (newState.isPlaying && current.lastUpdateTimeMs > 0L && newState.lastUpdateTimeMs > 0L) {
                val drift = abs(current.getEstimatedPositionMs(nowMs) - newState.getEstimatedPositionMs(nowMs))
                drift < CLOCK_DRIFT_TOLERANCE_MS
            } else {
                newState.currentPositionMs == current.currentPositionMs &&
                    epoch == current.trackEpoch
            }
        } else {
            false
        }

        if (isMetadataSame && isRedundantClockTick) {
            return current
        }

        if (current.coverUrl.isNullOrBlank() &&
            !newState.coverUrl.isNullOrBlank() &&
            currentCoverRank == CoverQuality.NONE.rank
        ) {
            currentCoverRank = CoverQuality.NATIVE_METADATA.rank
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
        return stamped
    }

    private inline fun updateMetadataField(transform: (TrackPlaybackState) -> TrackPlaybackState) {
        synchronized(stateLock) {
            val current = _playbackState.value
            val updated = transform(current)
            if (updated == current) return
            _playbackState.value = updated
            _trackMetadata.value = updated.toMetadata()
        }
    }

    /**
     * 原子更新播放队列，避免异步映射队列后使用过期的 State 快照覆盖当前曲目与 trackEpoch
     */
    fun updateQueueItems(items: List<QueueTrackItem>) {
        updateMetadataField { current ->
            if (current.queueItems == items) current else current.copy(queueItems = items)
        }
    }

    fun updateShuffleMode(isShuffleActive: Boolean) {
        updateMetadataField { current ->
            if (current.isShuffleActive == isShuffleActive) current else current.copy(isShuffleActive = isShuffleActive)
        }
    }

    fun updateRepeatMode(repeatMode: Int) {
        updateMetadataField { current ->
            if (current.repeatMode == repeatMode) current else current.copy(repeatMode = repeatMode)
        }
    }

    fun updatePlaybackModes(isShuffleActive: Boolean, repeatMode: Int) {
        updateMetadataField { current ->
            if (current.isShuffleActive == isShuffleActive && current.repeatMode == repeatMode) {
                current
            } else {
                current.copy(isShuffleActive = isShuffleActive, repeatMode = repeatMode)
            }
        }
    }

    fun updatePositionOnly(positionMs: Long, isPlaying: Boolean) {
        updatePlaybackClock(
            positionMs = positionMs,
            isPlaying = isPlaying,
            playbackSpeed = null,
            lastUpdateTimeMs = elapsedRealtimeProvider(),
            requireValidTrack = true
        )
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
