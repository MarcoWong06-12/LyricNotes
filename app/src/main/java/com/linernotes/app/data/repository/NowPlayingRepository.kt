package com.linernotes.app.data.repository

import android.os.SystemClock
import com.linernotes.app.LinerNotesApp
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricFragmentMatcher
import com.linernotes.app.core.lyric.LyricSanitizer
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.playback.CoverQuality
import com.linernotes.app.core.playback.PlaybackClockState
import com.linernotes.app.core.playback.PlaybackStateManager
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackMetadataState
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.core.util.HtmlUtils
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.remote.GeniusService
import com.linernotes.app.data.remote.KugouLyricsService
import com.linernotes.app.data.remote.LrclibLyricsService
import com.linernotes.app.data.remote.MetadataService
import com.linernotes.app.data.remote.NetEaseLyricsService
import com.linernotes.app.data.remote.QQMusicLyricsService
import com.linernotes.app.data.remote.OnlineLyricsResult
import com.linernotes.app.data.remote.TranslationService
import com.linernotes.app.data.remote.UnifiedLyricsService
import com.linernotes.app.domain.model.BilingualLyricLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class LyricsContentState(
    val trackEpoch: Long = 0L,
    val lyrics: List<BilingualLyricLine> = emptyList(),
    val annotatedLines: Map<Int, LyricAnnotationEntity> = emptyMap(),
    val songStory: SongStoryEntity? = null,
    val isLoadingLyrics: Boolean = false,
    val isLoadingGenius: Boolean = false,
    val geniusNoticeMessage: String? = null,
    val errorMessage: String? = null
)

@Immutable
data class NowPlayingData(
    val playbackState: TrackPlaybackState = TrackPlaybackState(),
    val lyrics: List<BilingualLyricLine> = emptyList(),
    val annotatedLines: Map<Int, LyricAnnotationEntity> = emptyMap(),
    val songStory: SongStoryEntity? = null,
    val isLoadingLyrics: Boolean = false,
    val isLoadingGenius: Boolean = false,
    val geniusNoticeMessage: String? = null,
    val currentLineIndex: Int = -1,
    val errorMessage: String? = null
)

/**
 * 核心 Now Playing 数据调度中心
 * 监听 Spotify 播放事件，并发调度多源歌词、缺失自动翻译补齐与 Genius 典故注释
 */
@Singleton
class NowPlayingRepository @Inject constructor(
    private val playbackStateManager: PlaybackStateManager,
    private val translationService: TranslationService,
    private val aiPreferences: AiPreferences,
    private val floatingPreferences: FloatingLyricsPreferences
) {

    private val repositoryScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            throwable.printStackTrace()
        }
    )

    val trackMetadataFlow: StateFlow<TrackMetadataState> = playbackStateManager.trackMetadata
    val playbackClockFlow: StateFlow<PlaybackClockState> = playbackStateManager.playbackClock
    val notificationAccessGrantedFlow: StateFlow<Boolean> = playbackStateManager.notificationAccessGranted

    private val _lyricsContentFlow = MutableStateFlow(LyricsContentState())
    val lyricsContentFlow: StateFlow<LyricsContentState> = _lyricsContentFlow.asStateFlow()

    private val _currentLineIndexFlow = MutableStateFlow(-1)
    val currentLineIndexFlow: StateFlow<Int> = _currentLineIndexFlow.asStateFlow()

    private val _nowPlayingData = MutableStateFlow(NowPlayingData())
    val nowPlayingData: StateFlow<NowPlayingData> = _nowPlayingData.asStateFlow()

    private var currentSongKey: String? = null
    @Volatile
    private var activeTrackEpoch: Long = 0L
    private var lastPreloadTriggerKey: String? = null
    private var lastAutoRetryTimeMs: Long = 0L
    private var dataLoadJob: Job? = null
    private var trackLoadDebounceJob: Job? = null
    private var positionTrackerJob: Job? = null
    private var noticeDismissJob: Job? = null

    // 内存高速缓存 (同一首歌二次播放 0ms 响应，LRU 淘汰上限 50 首，线程安全)
    private val memoryCache: MutableMap<String, CachedSongData> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, CachedSongData>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSongData>?): Boolean = size > 50
        }
    )

    private data class CachedSongData(
        val lyrics: List<BilingualLyricLine>,
        val annotatedLines: Map<Int, LyricAnnotationEntity>,
        val songStory: SongStoryEntity?,
        val geniusNotice: String? = null,
        val hasAttemptedGenius: Boolean = false
    )

    private var globalOffsetMs: Long = floatingPreferences.globalOffsetMs
    private var currentSongOffsetMs: Long = 0L
    private var lyricOffsetMs: Long = globalOffsetMs + currentSongOffsetMs

    private val _lyricOffsetMsFlow = MutableStateFlow(lyricOffsetMs)
    val lyricOffsetMsFlow: StateFlow<Long> = _lyricOffsetMsFlow.asStateFlow()

    private val _songOffsetMsFlow = MutableStateFlow(0L)
    val songOffsetMsFlow: StateFlow<Long> = _songOffsetMsFlow.asStateFlow()

    private val _globalOffsetMsFlow = MutableStateFlow(globalOffsetMs)
    val globalOffsetMsFlow: StateFlow<Long> = _globalOffsetMsFlow.asStateFlow()

    fun adjustSongOffset(deltaMs: Long) {
        setSongOffset(currentSongOffsetMs + deltaMs)
    }

    fun setSongOffset(offsetMs: Long) {
        currentSongOffsetMs = offsetMs
        _songOffsetMsFlow.value = offsetMs
        currentSongKey?.let { floatingPreferences.setSongOffset(it, offsetMs) }
        updateEffectiveOffset()
    }

    fun resetSongOffset() {
        setSongOffset(0L)
    }

    fun adjustGlobalOffset(deltaMs: Long) {
        setGlobalOffset(globalOffsetMs + deltaMs)
    }

    fun setGlobalOffset(offsetMs: Long) {
        globalOffsetMs = offsetMs
        floatingPreferences.globalOffsetMs = offsetMs
        _globalOffsetMsFlow.value = offsetMs
        updateEffectiveOffset()
    }

    fun resetGlobalOffset() {
        setGlobalOffset(0L)
    }

    fun setLyricOffset(offsetMs: Long) {
        setSongOffset(offsetMs)
    }

    private fun updateEffectiveOffset() {
        lyricOffsetMs = globalOffsetMs + currentSongOffsetMs
        _lyricOffsetMsFlow.value = lyricOffsetMs
    }

    init {
        // 1. 监听 Spotify 播放状态变更
        repositoryScope.launch {
            playbackStateManager.playbackState.collect { state ->
                handlePlaybackStateUpdate(state)
            }
        }

        // 2. 毫秒级时间轴驱动与高亮行定位 (60fps 级流畅推算)
        startPositionTracker()

        // 3. 初始载入全局歌词时间轴偏移 (默认 0ms 原生自然时间轴)
        globalOffsetMs = floatingPreferences.globalOffsetMs
        _globalOffsetMsFlow.value = globalOffsetMs
        updateEffectiveOffset()
    }

    private fun syncNowPlayingAggregate(
        playbackState: TrackPlaybackState = playbackStateManager.playbackState.value,
        content: LyricsContentState = _lyricsContentFlow.value,
        lineIndex: Int = _currentLineIndexFlow.value
    ) {
        _nowPlayingData.value = NowPlayingData(
            playbackState = playbackState,
            lyrics = content.lyrics,
            annotatedLines = content.annotatedLines,
            songStory = content.songStory,
            isLoadingLyrics = content.isLoadingLyrics,
            isLoadingGenius = content.isLoadingGenius,
            geniusNoticeMessage = content.geniusNoticeMessage,
            currentLineIndex = lineIndex,
            errorMessage = content.errorMessage
        )
    }

    private fun updateLineIndexOnly(lineIndex: Int) {
        if (_currentLineIndexFlow.value != lineIndex) {
            _currentLineIndexFlow.value = lineIndex
            _nowPlayingData.update { current ->
                if (current.currentLineIndex != lineIndex) current.copy(currentLineIndex = lineIndex) else current
            }
        }
    }

    private fun handlePlaybackStateUpdate(state: TrackPlaybackState) {
        if (!state.hasValidTrack) {
            currentSongKey = null
            activeTrackEpoch = state.trackEpoch
            dataLoadJob?.cancel()
            trackLoadDebounceJob?.cancel()
            val emptyContent = LyricsContentState(trackEpoch = state.trackEpoch)
            _lyricsContentFlow.value = emptyContent
            _currentLineIndexFlow.value = -1
            syncNowPlayingAggregate(playbackState = state, content = emptyContent, lineIndex = -1)
            return
        }

        val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
        val isNewSong = songKey != currentSongKey || state.trackEpoch != activeTrackEpoch

        if (isNewSong) {
            currentSongKey = songKey
            activeTrackEpoch = state.trackEpoch
            lastAutoRetryTimeMs = SystemClock.elapsedRealtime()
            dataLoadJob?.cancel()
            trackLoadDebounceJob?.cancel()
            noticeDismissJob?.cancel()

            val songOffset = floatingPreferences.getSongOffset(songKey)
            currentSongOffsetMs = songOffset
            _songOffsetMsFlow.value = songOffset
            updateEffectiveOffset()

            // 切歌瞬间原子清空上一曲歌词、典故、故事與失敗提示，徹底消除切歌殘影
            val cached = memoryCache[songKey]
            val isCachedIncomplete = isCacheTranslationIncomplete(cached)
            if (cached != null && !isCachedIncomplete && cached.lyrics.isNotEmpty() && cached.hasAttemptedGenius) {
                val safeLyrics = cached.lyrics.map { line ->
                    if (LyricAligner.isInvalidTranslationLine(line.translation)) line.copy(translation = "") else line
                }
                val currentPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                val lineIndex = calculateActiveLineIndex(safeLyrics, currentPos)
                val cachedContent = LyricsContentState(
                    trackEpoch = state.trackEpoch,
                    lyrics = safeLyrics,
                    annotatedLines = cached.annotatedLines,
                    songStory = cached.songStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false,
                    geniusNoticeMessage = cached.geniusNotice,
                    errorMessage = null
                )
                _lyricsContentFlow.value = cachedContent
                _currentLineIndexFlow.value = lineIndex
                syncNowPlayingAggregate(playbackState = state, content = cachedContent, lineIndex = lineIndex)
            } else {
                val resetContent = LyricsContentState(
                    trackEpoch = state.trackEpoch,
                    lyrics = emptyList(),
                    annotatedLines = emptyMap(),
                    songStory = null,
                    isLoadingLyrics = true,
                    isLoadingGenius = false,
                    geniusNoticeMessage = null,
                    errorMessage = null
                )
                _lyricsContentFlow.value = resetContent
                _currentLineIndexFlow.value = -1
                syncNowPlayingAggregate(playbackState = state, content = resetContent, lineIndex = -1)

                val targetEpoch = state.trackEpoch
                trackLoadDebounceJob = repositoryScope.launch {
                    delay(150L)
                    if (targetEpoch == activeTrackEpoch) {
                        loadSongLyricsAndGenius(state.title, state.artist, songKey, targetEpoch)
                    }
                }
            }
        } else {
            val curContent = _lyricsContentFlow.value
            val lineIndex = if (curContent.lyrics.isNotEmpty()) {
                val currentPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                calculateActiveLineIndex(curContent.lyrics, currentPos)
            } else -1
            _currentLineIndexFlow.value = lineIndex
            _nowPlayingData.update { current ->
                current.copy(playbackState = state, currentLineIndex = lineIndex)
            }

            // 自愈重試機制：若切歌瞬間遭遇斷網導致首次抓取落空，在播放狀態下每間隔 4 秒自動重試
            val nowElapsed = SystemClock.elapsedRealtime()
            if (curContent.lyrics.isEmpty() && !curContent.isLoadingLyrics && state.isPlaying && (nowElapsed - lastAutoRetryTimeMs > 4000L)) {
                lastAutoRetryTimeMs = nowElapsed
                loadSongLyricsAndGenius(state.title, state.artist, songKey, activeTrackEpoch)
            }
        }

        // 僅在實質切歌或佇列曲目變更時觸發預加載，杜絕播放/暫停/封面回填重覆風暴
        if (state.queueItems.isNotEmpty()) {
            val queueSig = state.queueItems.joinToString("|") { "${it.artist}:${it.title}" }
            val triggerKey = "$songKey||$queueSig"
            if (triggerKey != lastPreloadTriggerKey) {
                lastPreloadTriggerKey = triggerKey
                preloadNextTracks(state.queueItems)
            }
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = repositoryScope.launch {
            combine(
                LinerNotesApp.isForegroundFlow,
                FloatingLyricsService.isRunningFlow,
                _lyricsContentFlow.map { it.lyrics }.distinctUntilChanged(),
                playbackClockFlow,
                _lyricOffsetMsFlow
            ) { appInForeground, floatingRunning, lyrics, clock, offsetMs ->
                TrackerInput(
                    isObserverActive = appInForeground || floatingRunning,
                    lyrics = lyrics,
                    clock = clock,
                    offsetMs = offsetMs
                )
            }.collectLatest { input ->
                if (input.lyrics.isEmpty()) {
                    updateLineIndexOnly(-1)
                    return@collectLatest
                }

                val initialPos = (input.clock.getEstimatedPositionMs() + input.offsetMs).coerceAtLeast(0L)
                val initialIndex = calculateActiveLineIndex(input.lyrics, initialPos)
                updateLineIndexOnly(initialIndex)

                // 暫停中或螢幕退至後台且無懸浮窗時徹底掛起，0 CPU 喚醒
                if (!input.isObserverActive || !input.clock.isPlaying) {
                    return@collectLatest
                }

                val speed = input.clock.playbackSpeed.coerceAtLeast(0.25f)
                while (isActive) {
                    val curPos = (input.clock.getEstimatedPositionMs() + input.offsetMs).coerceAtLeast(0L)
                    val curIdx = calculateActiveLineIndex(input.lyrics, curPos)
                    updateLineIndexOnly(curIdx)

                    val waitMs = computeWaitUntilNextLineMs(input.lyrics, curIdx, curPos, speed)
                    delay(waitMs)
                }
            }
        }
    }

    private data class TrackerInput(
        val isObserverActive: Boolean,
        val lyrics: List<BilingualLyricLine>,
        val clock: PlaybackClockState,
        val offsetMs: Long
    )

    private fun computeWaitUntilNextLineMs(
        lyrics: List<BilingualLyricLine>,
        currentIndex: Int,
        currentPosMs: Long,
        playbackSpeed: Float
    ): Long {
        var nextTriggerMs: Long? = null
        val startSearch = (currentIndex + 1).coerceAtLeast(0)
        var prevStart = if (currentIndex in lyrics.indices) lyrics[currentIndex].startTimeMs ?: 0L else 0L
        for (i in startSearch until lyrics.size) {
            val st = lyrics[i].startTimeMs ?: continue
            val gap = if (currentIndex >= 0) (st - prevStart).coerceAtLeast(0L) else 2000L
            val lead = if (gap < 1400L) 45L else 75L
            val trigger = (st - lead).coerceAtLeast(0L)
            if (trigger > currentPosMs) {
                nextTriggerMs = trigger
                break
            }
            prevStart = st
        }
        return if (nextTriggerMs != null) {
            (((nextTriggerMs - currentPosMs) / playbackSpeed).toLong() + 4L).coerceIn(20L, 450L)
        } else {
            500L
        }
    }

    private fun isCacheTranslationIncomplete(cached: CachedSongData?): Boolean {
        if (cached == null) return false
        return (cached.lyrics.size > 5 &&
            cached.lyrics.count { it.translation.isNotBlank() && !LyricAligner.isInvalidTranslationLine(it.translation) } <= (cached.lyrics.size * 0.25).toInt()) ||
            cached.lyrics.any { LyricAligner.isInvalidTranslationLine(it.translation) }
    }

    /**
     * 计算当前活跃歌词行索引 (自适应人声提前量补偿：密集快嘴说唱采用 45ms 严密咬合，常规抒情句采用 75ms 自然呼吸补偿)
     */
    private fun calculateActiveLineIndex(lyrics: List<BilingualLyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var activeIndex = -1
        var prevStartTime = 0L
        for (i in lyrics.indices) {
            val startTime = lyrics[i].startTimeMs ?: continue
            val lineGap = if (activeIndex >= 0) (startTime - prevStartTime).coerceAtLeast(0L) else 2000L
            val adaptiveLeadMs = if (lineGap < 1400L) 45L else 75L
            if (startTime <= positionMs + adaptiveLeadMs) {
                activeIndex = i
                prevStartTime = startTime
            } else {
                break
            }
        }
        // 当处于曲目前奏期 (positionMs 小于第 1 句歌词时间 - 提前量) 时返回 -1，悬浮窗自然显示歌曲名与歌手，避免第1句提前几秒到几十秒显示
        return activeIndex
    }

    fun reloadCurrentSong() {
        val state = playbackStateManager.playbackState.value
        val songKey = currentSongKey ?: return
        forceReloadLyrics(state.title, state.artist, songKey, activeTrackEpoch)
    }

    fun forceReloadLyrics(
        title: String,
        artist: String,
        songKey: String,
        expectedEpoch: Long = activeTrackEpoch
    ) {
        val cached = memoryCache[songKey]
        if (cached != null) {
            // 清理歌词缓存但保留已抓取的 Genius 背景故事，防止重复拉取 Genius
            memoryCache[songKey] = cached.copy(lyrics = emptyList(), annotatedLines = emptyMap())
        } else {
            memoryCache.remove(songKey)
        }
        _lyricsContentFlow.update {
            it.copy(
                trackEpoch = expectedEpoch,
                isLoadingLyrics = true,
                errorMessage = null
            )
        }
        syncNowPlayingAggregate(content = _lyricsContentFlow.value)
        loadSongLyricsAndGenius(title, artist, songKey, expectedEpoch)
    }

    fun loadSongLyricsAndGenius(
        title: String,
        artist: String,
        songKey: String,
        trackEpoch: Long = activeTrackEpoch
    ) {
        dataLoadJob?.cancel()
        noticeDismissJob?.cancel()

        val cached = memoryCache[songKey]
        val isCachedTranslationSeverelyIncomplete = isCacheTranslationIncomplete(cached)

        if (cached != null && !isCachedTranslationSeverelyIncomplete && cached.lyrics.isNotEmpty() && cached.hasAttemptedGenius) {
            val safeLyrics = cached.lyrics.map { line ->
                if (LyricAligner.isInvalidTranslationLine(line.translation)) line.copy(translation = "") else line
            }
            val currentPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            val lineIndex = calculateActiveLineIndex(safeLyrics, currentPos)
            val content = LyricsContentState(
                trackEpoch = trackEpoch,
                lyrics = safeLyrics,
                annotatedLines = cached.annotatedLines,
                songStory = cached.songStory,
                isLoadingLyrics = false,
                isLoadingGenius = false,
                geniusNoticeMessage = cached.geniusNotice,
                errorMessage = null
            )
            _lyricsContentFlow.value = content
            _currentLineIndexFlow.value = lineIndex
            syncNowPlayingAggregate(content = content, lineIndex = lineIndex)
            return
        }

        if (cached != null && !isCachedTranslationSeverelyIncomplete && cached.lyrics.isNotEmpty()) {
            val safeLyrics = cached.lyrics.map { line ->
                if (LyricAligner.isInvalidTranslationLine(line.translation)) line.copy(translation = "") else line
            }
            val currentPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            val lineIndex = calculateActiveLineIndex(safeLyrics, currentPos)
            val content = LyricsContentState(
                trackEpoch = trackEpoch,
                lyrics = safeLyrics,
                annotatedLines = cached.annotatedLines,
                songStory = cached.songStory,
                isLoadingLyrics = false,
                isLoadingGenius = !cached.hasAttemptedGenius,
                geniusNoticeMessage = cached.geniusNotice,
                errorMessage = null
            )
            _lyricsContentFlow.value = content
            _currentLineIndexFlow.value = lineIndex
            syncNowPlayingAggregate(content = content, lineIndex = lineIndex)
        } else {
            val content = LyricsContentState(
                trackEpoch = trackEpoch,
                lyrics = emptyList(),
                annotatedLines = emptyMap(),
                songStory = cached?.songStory,
                isLoadingLyrics = true,
                isLoadingGenius = cached?.songStory == null,
                geniusNoticeMessage = cached?.geniusNotice,
                errorMessage = null
            )
            _lyricsContentFlow.value = content
            _currentLineIndexFlow.value = -1
            syncNowPlayingAggregate(content = content, lineIndex = -1)
        }

        dataLoadJob = repositoryScope.launch {
            if (trackEpoch != activeTrackEpoch) return@launch
            val hasCorruptedTranslation = cached?.lyrics?.any { LyricAligner.isInvalidTranslationLine(it.translation) } == true
            var fetchedLyrics: List<BilingualLyricLine> = if (hasCorruptedTranslation || isCachedTranslationSeverelyIncomplete) emptyList() else (cached?.lyrics ?: emptyList())
            var fetchedStory: SongStoryEntity? = cached?.songStory
            var fetchedAnnotations: List<LyricAnnotationEntity> = emptyList()

            // 1. 若歌词尚未就绪，并发抓取歌词与自动翻译补全
            val lyricsJob = async {
                if (fetchedLyrics.isNotEmpty()) return@async
                try {
                    val curState = playbackStateManager.playbackState.value
                    val manualBinding = aiPreferences.getManualLyricBinding(title, artist)
                    var rawResult: OnlineLyricsResult? = null

                    if (manualBinding != null) {
                        if (manualBinding.source == com.linernotes.app.domain.model.LyricSource.NETEASE.name) {
                            val songId = manualBinding.sourceId.toLongOrNull() ?: 0L
                            rawResult = NetEaseLyricsService.fetchLyricById(songId, title, artist)
                        } else if (manualBinding.source == com.linernotes.app.domain.model.LyricSource.QQ_MUSIC.name) {
                            rawResult = QQMusicLyricsService.fetchLyricByMid(
                                songmid = manualBinding.sourceId,
                                fallbackTitle = title,
                                fallbackArtist = artist,
                                albummid = manualBinding.extraKey,
                                durationMs = curState.durationMs
                            )
                        }
                    }

                    if (rawResult == null || rawResult.originalLyrics.isBlank()) {
                        rawResult = UnifiedLyricsService.fetchLyrics(
                            trackTitle = title,
                            artistName = artist,
                            sourcePref = aiPreferences.lyricsSource,
                            targetDurationMs = curState.durationMs
                        )

                        if (rawResult == null || rawResult.originalLyrics.isBlank()) {
                            kotlinx.coroutines.delay(300)
                            rawResult = NetEaseLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                ?: QQMusicLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                ?: KugouLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                ?: LrclibLyricsService.fetchLyrics(title, artist, curState.durationMs)
                        }
                    }

                    if (rawResult != null && rawResult.originalLyrics.isNotBlank()) {
                        val lyricCover = rawResult.coverUrl
                        if (!lyricCover.isNullOrBlank()) {
                            playbackStateManager.updateCoverIfBetter(
                                expectedTitle = title,
                                expectedArtist = artist,
                                coverUrl = lyricCover,
                                quality = CoverQuality.LYRIC_SOURCE,
                                expectedEpoch = trackEpoch
                            )
                        }

                        var origLyrics = rawResult.originalLyrics
                        var transLyrics = rawResult.translatedLyrics
                        if (transLyrics != null && (LyricAligner.isRefusalText(transLyrics) || transLyrics.lines().any { LyricAligner.isInvalidTranslationLine(it) })) {
                            transLyrics = null
                        }

                        // 如果抓取到的歌词没有中文翻译，优先向网易云与 QQ 音乐查询官方人工双语精翻！
                        val isOfficialTransLacking = transLyrics.isNullOrBlank() || run {
                            val origCount = origLyrics.lines().count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() }
                            val transCount = transLyrics?.lines()?.count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() } ?: 0
                            origCount > 4 && transCount <= (origCount * 0.35).toInt()
                        }
                        if (isOfficialTransLacking) {
                            try {
                                val officialBilingual = NetEaseLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                    ?.takeIf { !it.translatedLyrics.isNullOrBlank() }
                                    ?: QQMusicLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                    ?.takeIf { !it.translatedLyrics.isNullOrBlank() }
                                if (officialBilingual != null && !officialBilingual.translatedLyrics.isNullOrBlank()) {
                                    // 关键同源对齐保护：若官方双语源自带 LRC 时间轴，必须将原文与译文一并切换为同源版本，
                                    // 严禁将 LRCLIB 的断句原文与网易云/QQ 的译文强行混拼！
                                    if (officialBilingual.originalLyrics.contains(LyricAligner.LRC_TIMESTAMP_REGEX) ||
                                        !origLyrics.contains(LyricAligner.LRC_TIMESTAMP_REGEX)
                                    ) {
                                        origLyrics = officialBilingual.originalLyrics
                                    }
                                    transLyrics = officialBilingual.translatedLyrics
                                }
                            } catch (e: Exception) {
                                // 忽略
                            }
                        }

                        // 若官方平台仍无人工译文或译文严重残缺，触发内置极速翻译引擎（带序号硬锚定，零错位）
                        val isNeedMachineTrans = transLyrics.isNullOrBlank() || LyricAligner.isRefusalText(transLyrics) || run {
                            val origCount = origLyrics.lines().count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() }
                            val transCount = transLyrics?.lines()?.count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() } ?: 0
                            origCount > 4 && transCount <= (origCount * 0.35).toInt()
                        }
                        if (isNeedMachineTrans) {
                            try {
                                val transResult = translationService.translateTrack(title, origLyrics)
                                if (transResult != null && transResult.translatedLyrics.isNotBlank() && !LyricAligner.isRefusalText(transResult.translatedLyrics)) {
                                    transLyrics = transResult.translatedLyrics
                                }
                            } catch (e: Exception) {
                                // 容灾降级
                            }
                        }

                        // 脏字脱敏反屏蔽处理
                        val cleanOrig = if (aiPreferences.isDeCensorEnabled) {
                            LyricSanitizer.decensorLyrics(origLyrics, uncensoredRefLyrics = null, translationLyrics = transLyrics)
                        } else origLyrics
                        val cleanTrans = if (aiPreferences.isDeCensorEnabled) {
                            LyricSanitizer.decensorChineseLyrics(transLyrics, cleanOrig)
                        } else transLyrics

                        // 结构化时间轴对齐
                        fetchedLyrics = LyricAligner.align(cleanOrig, cleanTrans)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 并发抓取 Genius 歌曲创作背景与歌词行典故
            var geniusSongFound = false
            var currentSongId: Long? = null
            val geniusJob = async {
                try {
                    val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }
                    val searchResult = GeniusService.searchSong(title, artist, customToken)
                    if (searchResult != null) {
                        geniusSongFound = true
                        val songId = searchResult.id
                        currentSongId = songId

                        // 回填 Genius 封面（低優先級，不覆寫 iTunes / 歌詞源高清封面）
                        val cloudCover = searchResult.coverUrl ?: searchResult.thumbUrl
                        if (!cloudCover.isNullOrBlank()) {
                            playbackStateManager.updateCoverIfBetter(
                                expectedTitle = title,
                                expectedArtist = artist,
                                coverUrl = cloudCover,
                                quality = CoverQuality.GENIUS,
                                expectedEpoch = trackEpoch
                            )
                        }

                        // 高并发拉取 Genius 数据 (背景故事与行内典故同时请求，网络耗时直接减半)
                        coroutineScope {
                            val detailDeferred = async { GeniusService.getSongDetails(songId, customToken) }
                            val referentsDeferred = async { GeniusService.getReferents(songId, customToken) }

                            val songDetail = detailDeferred.await()
                            val referents = referentsDeferred.await()

                            if (songDetail != null) {
                                fetchedStory = SongStoryEntity(
                                    trackId = songId,
                                    geniusSongId = songId,
                                    title = songDetail.title,
                                    artist = songDetail.artist,
                                    descriptionPlain = songDetail.descriptionPlain,
                                    descriptionTranslation = null,
                                    releaseDate = songDetail.releaseDate,
                                    headerImageUrl = songDetail.headerImageUrl,
                                    songArtImageUrl = songDetail.songArtImageUrl,
                                    producerCredits = songDetail.producerCredits,
                                    songUrl = songDetail.songUrl
                                )
                            }

                            val rawAnnotList = mutableListOf<LyricAnnotationEntity>()
                            for (ref in referents) {
                                val annotItem = ref.annotations.firstOrNull() ?: continue
                                rawAnnotList.add(
                                    LyricAnnotationEntity(
                                        trackId = songId,
                                        lyricFragment = ref.fragment,
                                        explanationText = annotItem.bodyPlain,
                                        authorName = annotItem.authorName,
                                        authorAvatarUrl = annotItem.authorAvatarUrl,
                                        isVerified = annotItem.verified,
                                        votesTotal = annotItem.votesTotal,
                                        explanationTranslation = null,
                                        geniusSongId = songId,
                                        geniusUrl = annotItem.url
                                    )
                                )
                            }
                            fetchedAnnotations = rawAnnotList
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. 并发回填超高清官方原版专辑封面（优先 iTunes/Apple Music 600x600/1000x1000 原图，兜底网易云音乐）
            val artworkJob = async {
                try {
                    val onlineCover = MetadataService.fetchTrackCover(title, artist)
                    if (!onlineCover.isNullOrBlank()) {
                        playbackStateManager.updateCoverIfBetter(
                            expectedTitle = title,
                            expectedArtist = artist,
                            coverUrl = onlineCover,
                            quality = CoverQuality.ITUNES_HD,
                            expectedEpoch = trackEpoch
                        )
                    }
                } catch (_: Exception) {
                }
            }

            // 等待并发抓取完成
            lyricsJob.await()
            if (trackEpoch != activeTrackEpoch) return@launch

            val curPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            val lineIdx = calculateActiveLineIndex(fetchedLyrics, curPos)
            _lyricsContentFlow.update {
                it.copy(
                    trackEpoch = trackEpoch,
                    lyrics = fetchedLyrics,
                    isLoadingLyrics = false
                )
            }
            _currentLineIndexFlow.value = lineIdx
            syncNowPlayingAggregate(content = _lyricsContentFlow.value, lineIndex = lineIdx)

            geniusJob.await()
            artworkJob.await()
            if (trackEpoch != activeTrackEpoch) return@launch

            // 阶段一：即刻渲染原生典故徽标与背景故事按钮（1秒级展示，绝不让用户等待逐条翻译完成）
            val initialMatched = if (fetchedLyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
                LyricFragmentMatcher.matchAnnotationsToLines(fetchedLyrics, fetchedAnnotations)
            } else {
                emptyMap()
            }

            val currentStory = fetchedStory
            val failureNotice: String? = when {
                initialMatched.isNotEmpty() || (currentStory != null && currentStory.descriptionPlain.isNotBlank()) -> null
                !GeniusService.lastRequestConnected -> "Genius 网络连接受阻，无法获取典故"
                !geniusSongFound -> "未在 Genius 检索到本曲收录"
                fetchedAnnotations.isEmpty() -> "Genius 尚未收录本曲歌词典故"
                else -> "Genius 典故与当前歌词版本未能成功匹配"
            }

            if (fetchedLyrics.isNotEmpty()) {
                val preliminaryData = CachedSongData(
                    lyrics = fetchedLyrics,
                    annotatedLines = initialMatched,
                    songStory = fetchedStory,
                    geniusNotice = failureNotice,
                    hasAttemptedGenius = true
                )
                memoryCache[songKey] = preliminaryData
            }

            val updatedPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            val updatedIdx = calculateActiveLineIndex(fetchedLyrics, updatedPos)
            val stage1Content = _lyricsContentFlow.value.copy(
                trackEpoch = trackEpoch,
                lyrics = fetchedLyrics,
                annotatedLines = initialMatched,
                songStory = fetchedStory,
                isLoadingLyrics = false,
                isLoadingGenius = false,
                geniusNoticeMessage = failureNotice
            )
            _lyricsContentFlow.value = stage1Content
            _currentLineIndexFlow.value = updatedIdx
            syncNowPlayingAggregate(content = stage1Content, lineIndex = updatedIdx)

            // 若有失败提醒，7 秒后自动淡化提示，避免持续遮挡歌词
            if (failureNotice != null) {
                noticeDismissJob?.cancel()
                noticeDismissJob = repositoryScope.launch {
                    delay(7000)
                    if (trackEpoch == activeTrackEpoch && _lyricsContentFlow.value.geniusNoticeMessage == failureNotice) {
                        _lyricsContentFlow.update { it.copy(geniusNoticeMessage = null) }
                        syncNowPlayingAggregate(content = _lyricsContentFlow.value)
                    }
                }
            }

            // 阶段二：后台受控并发极速补全中文对照翻译 (4 协程并行，1.5 秒内全部就绪并平滑刷新界面)
            if (fetchedStory != null || fetchedAnnotations.isNotEmpty()) {
                val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
                val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
                    TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW ||
                    aiPreferences.isTraditionalChinese
                val transSemaphore = Semaphore(4)
                coroutineScope {
                    val storyTransDeferred = async {
                        val cStory = fetchedStory
                        if (cStory != null && cStory.descriptionTranslation.isNullOrBlank() &&
                            cStory.descriptionPlain.isNotBlank() && !AiAnnotationCurator.isAlreadyChinese(cStory.descriptionPlain)
                        ) {
                            transSemaphore.withPermit {
                                try {
                                    val tr = translationService.translateText(cStory.descriptionPlain, targetIso)
                                    if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || CJK_REGEX.containsMatchIn(tr))) {
                                        val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                                        val finalStoryTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                        fetchedStory = cStory.copy(descriptionTranslation = finalStoryTrans)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }

                    val annotTransDeferredList = fetchedAnnotations.map { annot ->
                        async {
                            if (annot.explanationTranslation.isNullOrBlank() &&
                                annot.explanationText.isNotBlank() && !AiAnnotationCurator.isAlreadyChinese(annot.explanationText)
                            ) {
                                transSemaphore.withPermit {
                                    try {
                                        val tr = translationService.translateText(annot.explanationText, targetIso)
                                        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || CJK_REGEX.containsMatchIn(tr))) {
                                            val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                                            val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                            annot.copy(explanationTranslation = finalTrans)
                                        } else {
                                            annot
                                        }
                                    } catch (_: Exception) {
                                        annot
                                    }
                                }
                            } else {
                                annot
                            }
                        }
                    }

                    storyTransDeferred.await()
                    fetchedAnnotations = annotTransDeferredList.awaitAll()
                }

                val finalMatched = if (fetchedLyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
                    LyricFragmentMatcher.matchAnnotationsToLines(fetchedLyrics, fetchedAnnotations)
                } else {
                    initialMatched
                }

                if (fetchedLyrics.isNotEmpty()) {
                    memoryCache[songKey] = CachedSongData(
                        lyrics = fetchedLyrics,
                        annotatedLines = finalMatched,
                        songStory = fetchedStory,
                        geniusNotice = failureNotice,
                        hasAttemptedGenius = true
                    )
                }

                if (trackEpoch == activeTrackEpoch) {
                    _lyricsContentFlow.update {
                        it.copy(
                            annotatedLines = finalMatched,
                            songStory = fetchedStory
                        )
                    }
                    syncNowPlayingAggregate(content = _lyricsContentFlow.value)
                }
            }
        }
    }

    private var preloadingJob: Job? = null

    private fun preloadNextTracks(queueItems: List<com.linernotes.app.core.playback.QueueTrackItem>) {
        if (queueItems.isEmpty()) return
        preloadingJob?.cancel()
        preloadingJob = repositoryScope.launch {
            // 关键：让出前 2.5 秒最高优先级网络带宽与连接池给当前播放曲目！
            delay(2500L)
            val currentKey = currentSongKey
            // 提取待播队列中接下来的 3 首曲目进行深度静默预加载 (并发预载歌词、翻译、Genius 典故与背景故事)
            val candidates = queueItems.filter { item ->
                if (item.title.isBlank() || item.artist.isBlank()) return@filter false
                val key = "${item.artist.trim().lowercase()} - ${item.title.trim().lowercase()}"
                val cached = memoryCache[key]
                key != currentKey && (cached == null || (!cached.hasAttemptedGenius && cached.annotatedLines.isEmpty() && cached.songStory == null))
            }.take(3)

            for (item in candidates) {
                val nextKey = "${item.artist.trim().lowercase()} - ${item.title.trim().lowercase()}"
                try {
                    silentFetchAndCacheSongFull(item.title, item.artist, nextKey)
                } catch (e: Exception) {
                    // 静默容灾
                }
            }
        }
    }

    private suspend fun silentFetchAndCacheSongFull(title: String, artist: String, songKey: String) {
        // 1. 检查或静默拉取歌词与翻译
        val cachedInMem = memoryCache[songKey]
        val isCachedTranslationSeverelyIncomplete = cachedInMem != null &&
            cachedInMem.lyrics.size > 5 &&
            cachedInMem.lyrics.count { it.translation.isNotBlank() } <= (cachedInMem.lyrics.size * 0.25).toInt()

        var lyrics = if (!isCachedTranslationSeverelyIncomplete) cachedInMem?.lyrics ?: emptyList() else emptyList()
        if (lyrics.isEmpty()) {
            val rawResult = UnifiedLyricsService.fetchLyrics(
                trackTitle = title,
                artistName = artist,
                sourcePref = aiPreferences.lyricsSource
            ) ?: return

            if (rawResult.originalLyrics.isBlank()) return

            val matchScore = LyricSearchCleaner.scoreCandidateMatch(
                candidateTitle = rawResult.title,
                candidateArtist = rawResult.artist,
                targetTitle = title,
                targetArtist = artist
            )
            if (matchScore < 50) return

            var origLyrics = rawResult.originalLyrics
            var transLyrics = rawResult.translatedLyrics
            if (transLyrics != null && (LyricAligner.isRefusalText(transLyrics) || transLyrics.lines().any { LyricAligner.isInvalidTranslationLine(it) })) {
                transLyrics = null
            }

            // 如果抓取到的歌词没有中文翻译，优先向网易云与 QQ 音乐查询官方人工双语精翻！
            val isOfficialTransLacking = transLyrics.isNullOrBlank() || run {
                val origCount = origLyrics.lines().count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() }
                val transCount = transLyrics?.lines()?.count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() } ?: 0
                origCount > 4 && transCount <= (origCount * 0.35).toInt()
            }
            if (isOfficialTransLacking) {
                try {
                    val officialBilingual = NetEaseLyricsService.fetchLyrics(title, artist)
                        ?.takeIf { !it.translatedLyrics.isNullOrBlank() }
                        ?: QQMusicLyricsService.fetchLyrics(title, artist)
                        ?.takeIf { !it.translatedLyrics.isNullOrBlank() }
                    if (officialBilingual != null && !officialBilingual.translatedLyrics.isNullOrBlank()) {
                        if (officialBilingual.originalLyrics.contains(LyricAligner.LRC_TIMESTAMP_REGEX) ||
                            !origLyrics.contains(LyricAligner.LRC_TIMESTAMP_REGEX)
                        ) {
                            origLyrics = officialBilingual.originalLyrics
                        }
                        transLyrics = officialBilingual.translatedLyrics
                    }
                } catch (e: Exception) {
                    // 忽略
                }
            }

            // 若官方平台仍无人工译文或译文严重残缺，触发内置极速翻译引擎（带序号硬锚定，零错位）
            val isNeedMachineTrans = transLyrics.isNullOrBlank() || LyricAligner.isRefusalText(transLyrics) || run {
                val origCount = origLyrics.lines().count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() }
                val transCount = transLyrics?.lines()?.count { it.replace(LyricAligner.LRC_TIMESTAMP_REGEX, "").trim().isNotBlank() } ?: 0
                origCount > 4 && transCount <= (origCount * 0.35).toInt()
            }
            if (isNeedMachineTrans) {
                try {
                    val transResult = translationService.translateTrack(title, origLyrics)
                    if (transResult != null && transResult.translatedLyrics.isNotBlank() && !LyricAligner.isRefusalText(transResult.translatedLyrics)) {
                        transLyrics = transResult.translatedLyrics
                    }
                } catch (e: Exception) {
                    // 忽略
                }
            }

            val cleanOrig = if (aiPreferences.isDeCensorEnabled) {
                LyricSanitizer.decensorLyrics(origLyrics, uncensoredRefLyrics = null, translationLyrics = transLyrics)
            } else origLyrics
            val cleanTrans = if (aiPreferences.isDeCensorEnabled) {
                LyricSanitizer.decensorChineseLyrics(transLyrics, cleanOrig)
            } else transLyrics
            lyrics = LyricAligner.align(cleanOrig, cleanTrans)
        }

        // 2. 静默拉取 Genius 歌曲创作背景与行内典故（含自动翻译）
        var fetchedStory: SongStoryEntity? = null
        var fetchedAnnotations: List<LyricAnnotationEntity> = emptyList()
        val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }

        try {
            val searchResult = GeniusService.searchSong(title, artist, customToken)
            if (searchResult != null) {
                val songId = searchResult.id

                // 并发拉取 Details 和 Referents (网络往返时间减半)
                coroutineScope {
                    val detailDeferred = async { GeniusService.getSongDetails(songId, customToken) }
                    val referentsDeferred = async { GeniusService.getReferents(songId, customToken) }

                    val songDetail = detailDeferred.await()
                    val referents = referentsDeferred.await()

                    if (songDetail != null && songDetail.descriptionPlain.isNotBlank()) {
                        fetchedStory = SongStoryEntity(
                            trackId = songId,
                            geniusSongId = songId,
                            title = songDetail.title,
                            artist = songDetail.artist,
                            descriptionPlain = songDetail.descriptionPlain,
                            descriptionTranslation = null,
                            releaseDate = songDetail.releaseDate,
                            headerImageUrl = songDetail.headerImageUrl,
                            songArtImageUrl = songDetail.songArtImageUrl,
                            producerCredits = songDetail.producerCredits,
                            songUrl = songDetail.songUrl
                        )
                    }

                    val annotList = mutableListOf<LyricAnnotationEntity>()
                    for (ref in referents) {
                        val annotItem = ref.annotations.firstOrNull() ?: continue
                        annotList.add(
                            LyricAnnotationEntity(
                                trackId = songId,
                                lyricFragment = ref.fragment,
                                explanationText = annotItem.bodyPlain,
                                authorName = annotItem.authorName,
                                authorAvatarUrl = annotItem.authorAvatarUrl,
                                isVerified = annotItem.verified,
                                votesTotal = annotItem.votesTotal,
                                explanationTranslation = null,
                                geniusSongId = songId,
                                geniusUrl = annotItem.url
                            )
                        )
                    }
                    fetchedAnnotations = annotList
                }

                // 并发极速翻译
                val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
                val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
                    TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW ||
                    aiPreferences.isTraditionalChinese
                val transSemaphore = Semaphore(4)
                coroutineScope {
                    val storyTransDeferred = async {
                        val curStory = fetchedStory
                        if (curStory != null && curStory.descriptionPlain.isNotBlank() &&
                            !AiAnnotationCurator.isAlreadyChinese(curStory.descriptionPlain)
                        ) {
                            transSemaphore.withPermit {
                                try {
                                    val tr = translationService.translateText(curStory.descriptionPlain, targetIso)
                                    if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || CJK_REGEX.containsMatchIn(tr))) {
                                        val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                                        val finalTr = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                        fetchedStory = curStory.copy(descriptionTranslation = finalTr)
                                    }
                                } catch (e: Exception) { }
                            }
                        }
                    }

                    val annotTransDeferredList = fetchedAnnotations.map { annot ->
                        async {
                            if (annot.explanationText.isNotBlank() && !AiAnnotationCurator.isAlreadyChinese(annot.explanationText)) {
                                transSemaphore.withPermit {
                                    try {
                                        val tr = translationService.translateText(annot.explanationText, targetIso)
                                        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || CJK_REGEX.containsMatchIn(tr))) {
                                            val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                                            val finalTr = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                            annot.copy(explanationTranslation = finalTr)
                                        } else {
                                            annot
                                        }
                                    } catch (e: Exception) {
                                        annot
                                    }
                                }
                            } else {
                                annot
                            }
                        }
                    }

                    storyTransDeferred.await()
                    fetchedAnnotations = annotTransDeferredList.awaitAll()
                }
            }
        } catch (e: Exception) {
            // 静默容灾
        }

        // 3. 典故行级精准匹配
        val matchedAnnotations = if (lyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
            LyricFragmentMatcher.matchAnnotationsToLines(lyrics, fetchedAnnotations)
        } else {
            emptyMap()
        }

        // 4. 存入内存缓存（后续切歌秒开无延迟）
        val cached = CachedSongData(
            lyrics = lyrics,
            annotatedLines = matchedAnnotations,
            songStory = fetchedStory,
            geniusNotice = null,
            hasAttemptedGenius = true
        )
        memoryCache[songKey] = cached

        // 若当前播放中的歌曲恰好是预加载完成的曲目，实时刷新界面
        if (currentSongKey == songKey) {
            val curPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            val lineIdx = calculateActiveLineIndex(lyrics, curPos)
            _lyricsContentFlow.update {
                it.copy(
                    lyrics = lyrics,
                    annotatedLines = matchedAnnotations,
                    songStory = fetchedStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false
                )
            }
            _currentLineIndexFlow.value = lineIdx
            syncNowPlayingAggregate(content = _lyricsContentFlow.value, lineIndex = lineIdx)
        }
    }

    fun dismissGeniusNotice() {
        noticeDismissJob?.cancel()
        _lyricsContentFlow.update { it.copy(geniusNoticeMessage = null) }
        syncNowPlayingAggregate(content = _lyricsContentFlow.value)
    }

    fun reloadGeniusOnly() {
        val state = playbackStateManager.trackMetadata.value
        val content = _lyricsContentFlow.value
        if (!state.hasValidTrack) return
        if (content.isLoadingGenius) return

        val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
        val existingLyrics = memoryCache[songKey]?.lyrics ?: content.lyrics

        // 立即进入加载中状态并更新通知，向用户提供即时正反馈
        val loadingNotice = "正在检索 Genius 典故..."
        _lyricsContentFlow.update {
            it.copy(
                isLoadingGenius = true,
                geniusNoticeMessage = loadingNotice
            )
        }
        syncNowPlayingAggregate(content = _lyricsContentFlow.value)

        if (existingLyrics.isNotEmpty()) {
            memoryCache[songKey] = CachedSongData(
                lyrics = existingLyrics,
                annotatedLines = emptyMap(),
                songStory = null,
                geniusNotice = loadingNotice,
                hasAttemptedGenius = false
            )
        } else {
            memoryCache.remove(songKey)
        }
        loadSongLyricsAndGenius(state.title, state.artist, songKey, state.trackEpoch)
    }

    suspend fun translateSingleAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW ||
            aiPreferences.isTraditionalChinese

        var updated = annotation

        // 1. 若歌词原句本身未翻译且非中文，异步请求翻译歌词原句
        if (updated.lyricTranslation.isNullOrBlank() && !AiAnnotationCurator.isAlreadyChinese(updated.lyricFragment)) {
            val transLyric = translationService.translateText(updated.lyricFragment, targetIso)
            if (!transLyric.isNullOrBlank()) {
                val cleanedTrans = HtmlUtils.cleanTranslationOutput(transLyric)
                val finalLyric = if (isTraditionalTarget) ChineseConverter.toTraditional(cleanedTrans) else cleanedTrans
                updated = updated.copy(lyricTranslation = finalLyric)
            }
        }

        // 2. 翻译典故解说正文
        if (!AiAnnotationCurator.isAlreadyChinese(updated.explanationText)) {
            val tr = translationService.translateText(updated.explanationText, targetIso)
            if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || CJK_REGEX.containsMatchIn(tr))) {
                val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                val finalTr = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                updated = updated.copy(explanationTranslation = finalTr)
            }
        }

        if (updated != annotation) {
            val replaceMatchingAnnotation: (Map<Int, LyricAnnotationEntity>) -> Map<Int, LyricAnnotationEntity> = { map ->
                map.mapValues { (_, v) ->
                    if (v.id == annotation.id || (v.lyricFragment == annotation.lyricFragment && v.explanationText == annotation.explanationText)) updated else v
                }
            }
            _lyricsContentFlow.update { current ->
                current.copy(annotatedLines = replaceMatchingAnnotation(current.annotatedLines))
            }
            syncNowPlayingAggregate(content = _lyricsContentFlow.value)

            val curKey = currentSongKey
            if (curKey != null) {
                memoryCache[curKey]?.let { cached ->
                    memoryCache[curKey] = cached.copy(
                        annotatedLines = replaceMatchingAnnotation(cached.annotatedLines)
                    )
                }
            }
        }
        return@withContext updated
    }

    suspend fun translateCurrentStory(story: SongStoryEntity): SongStoryEntity = withContext(Dispatchers.IO) {
        if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
            return@withContext story
        }
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW ||
            aiPreferences.isTraditionalChinese

        val tr = translationService.translateText(story.descriptionPlain, targetIso)
        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || CJK_REGEX.containsMatchIn(tr))) {
            val cleaned = HtmlUtils.cleanTranslationOutput(tr)
            val finalTr = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
            val updated = story.copy(descriptionTranslation = finalTr)

            _lyricsContentFlow.update { it.copy(songStory = updated) }
            syncNowPlayingAggregate(content = _lyricsContentFlow.value)

            val curKey = currentSongKey
            if (curKey != null) {
                memoryCache[curKey]?.let { cached ->
                    memoryCache[curKey] = cached.copy(songStory = updated)
                }
            }
            return@withContext updated
        }
        return@withContext story
    }

    // 播控方法代理回传 Spotify
    fun play() = playbackStateManager.play()
    fun pause() = playbackStateManager.pause()
    fun togglePlayPause() = playbackStateManager.togglePlayPause()
    fun skipToNext() = playbackStateManager.skipToNext()
    fun skipToPrevious() = playbackStateManager.skipToPrevious()
    fun seekTo(positionMs: Long) = playbackStateManager.seekTo(positionMs)
    fun toggleShuffle() = playbackStateManager.toggleShuffle()
    fun cycleRepeatMode() = playbackStateManager.cycleRepeatMode()
    fun skipToQueueItem(item: QueueTrackItem) = playbackStateManager.skipToQueueItem(item)

    fun hasManualBinding(title: String, artist: String): Boolean =
        aiPreferences.hasManualLyricBinding(title, artist)

    suspend fun searchLyricCandidates(query: String): List<com.linernotes.app.domain.model.LyricCandidateItem> {
        val curDuration = playbackStateManager.playbackState.value.durationMs
        return UnifiedLyricsService.searchLyricCandidates(query, curDuration)
    }

    suspend fun applyManualCandidate(
        candidate: com.linernotes.app.domain.model.LyricCandidateItem,
        trackTitle: String,
        artistName: String
    ): Boolean = withContext(Dispatchers.IO) {
        aiPreferences.saveManualLyricBinding(
            title = trackTitle,
            artist = artistName,
            source = candidate.source.name,
            sourceId = candidate.sourceId,
            extraKey = candidate.extraKey
        )

        val rawResult = UnifiedLyricsService.fetchLyricForCandidate(candidate)
        if (rawResult != null && rawResult.originalLyrics.isNotBlank()) {
            var origLyrics = rawResult.originalLyrics
            var transLyrics = rawResult.translatedLyrics
            if (transLyrics != null && (LyricAligner.isRefusalText(transLyrics) || transLyrics.lines().any { LyricAligner.isInvalidTranslationLine(it) })) {
                transLyrics = null
            }

            if (transLyrics.isNullOrBlank() || LyricAligner.isRefusalText(transLyrics)) {
                try {
                    val curDuration = playbackStateManager.playbackState.value.durationMs
                    val officialBilingual = NetEaseLyricsService.fetchLyrics(trackTitle, artistName, curDuration)
                        ?.takeIf { !it.translatedLyrics.isNullOrBlank() }
                        ?: QQMusicLyricsService.fetchLyrics(trackTitle, artistName, curDuration)
                        ?.takeIf { !it.translatedLyrics.isNullOrBlank() }
                    if (officialBilingual != null && !officialBilingual.translatedLyrics.isNullOrBlank()) {
                        transLyrics = officialBilingual.translatedLyrics
                    } else {
                        val transResult = translationService.translateTrack(trackTitle, origLyrics)
                        if (transResult != null && transResult.translatedLyrics.isNotBlank() && !LyricAligner.isRefusalText(transResult.translatedLyrics)) {
                            transLyrics = transResult.translatedLyrics
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            val aligned = LyricAligner.align(origLyrics, transLyrics)
            if (aligned.isNotEmpty()) {
                val songKey = "${artistName.trim().lowercase()} - ${trackTitle.trim().lowercase()}"
                val prevContent = _lyricsContentFlow.value
                val prevCache = memoryCache[songKey]
                val updatedCache = CachedSongData(
                    lyrics = aligned,
                    annotatedLines = prevCache?.annotatedLines ?: prevContent.annotatedLines,
                    songStory = prevCache?.songStory ?: prevContent.songStory,
                    geniusNotice = prevCache?.geniusNotice ?: prevContent.geniusNoticeMessage,
                    hasAttemptedGenius = prevCache?.hasAttemptedGenius ?: false
                )
                memoryCache[songKey] = updatedCache
                if (currentSongKey == songKey) {
                    val curPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                    val lineIdx = calculateActiveLineIndex(aligned, curPos)
                    _lyricsContentFlow.update {
                        it.copy(
                            lyrics = aligned,
                            isLoadingLyrics = false
                        )
                    }
                    _currentLineIndexFlow.value = lineIdx
                    syncNowPlayingAggregate(content = _lyricsContentFlow.value, lineIndex = lineIdx)
                }
                if (!candidate.coverUrl.isNullOrBlank()) {
                    playbackStateManager.updateCoverIfBetter(
                        expectedTitle = trackTitle,
                        expectedArtist = artistName,
                        coverUrl = candidate.coverUrl,
                        quality = CoverQuality.LYRIC_SOURCE
                    )
                }
                return@withContext true
            }
        }
        return@withContext false
    }

    suspend fun resetManualLyricBinding(trackTitle: String, artistName: String): Unit = withContext(Dispatchers.IO) {
        aiPreferences.clearManualLyricBinding(trackTitle, artistName)
        val songKey = "${artistName.trim().lowercase()} - ${trackTitle.trim().lowercase()}"
        forceReloadLyrics(trackTitle, artistName, songKey, playbackStateManager.trackMetadata.value.trackEpoch)
    }

    companion object {
        private val CJK_REGEX = Regex("""[\u4e00-\u9fa5]""")
    }
}

