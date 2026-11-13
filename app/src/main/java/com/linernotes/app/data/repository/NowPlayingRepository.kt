package com.linernotes.app.data.repository

import android.os.SystemClock
import com.linernotes.app.LinerNotesApp
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.i18n.NowPlayingMessageKeys
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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

    @Volatile
    private var currentSongKey: String? = null
    @Volatile
    private var activeTrackEpoch: Long = 0L
    private var lastPreloadTriggerKey: String? = null
    private var lastAutoRetryTimeMs: Long = 0L
    private var dataLoadJob: Job? = null
    private var trackLoadDebounceJob: Job? = null
    private var positionTrackerJob: Job? = null
    private var noticeDismissJob: Job? = null
    private var preloadingJob: Job? = null

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

    @Volatile
    private var globalOffsetMs: Long = floatingPreferences.globalOffsetMs
    @Volatile
    private var currentSongOffsetMs: Long = 0L
    @Volatile
    private var lyricOffsetMs: Long = globalOffsetMs + currentSongOffsetMs

    private val _lyricOffsetMsFlow = MutableStateFlow(lyricOffsetMs)
    val lyricOffsetMsFlow: StateFlow<Long> = _lyricOffsetMsFlow.asStateFlow()

    private val _songOffsetMsFlow = MutableStateFlow(0L)
    val songOffsetMsFlow: StateFlow<Long> = _songOffsetMsFlow.asStateFlow()

    private val _globalOffsetMsFlow = MutableStateFlow(globalOffsetMs)
    val globalOffsetMsFlow: StateFlow<Long> = _globalOffsetMsFlow.asStateFlow()

    val isWordByWordEnabledFlow: StateFlow<Boolean> = aiPreferences.isWordByWordEnabledFlow

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

    private fun buildCacheKey(songKey: String): String =
        "$songKey|${aiPreferences.lyricsSource}|${aiPreferences.targetLanguage}"

    private fun isChineseTargetIso(targetIso: String): Boolean =
        targetIso.startsWith("zh", ignoreCase = true)

    private fun isValidTranslatedText(translated: String?, source: String, targetIso: String): Boolean {
        if (translated.isNullOrBlank()) return false
        val cleaned = translated.trim()
        if (cleaned.equals(source.trim(), ignoreCase = true)) return false
        return if (isChineseTargetIso(targetIso)) {
            AiAnnotationCurator.isAlreadyChinese(cleaned) || CJK_REGEX.containsMatchIn(cleaned)
        } else {
            cleaned.any { it.isLetter() }
        }
    }

    private data class ProseTranslationSpec(
        val targetIso: String,
        val isChineseTarget: Boolean,
        val isTraditionalTarget: Boolean
    )

    private fun currentProseTranslationSpec(): ProseTranslationSpec {
        val prefCode = aiPreferences.targetLanguage
        val resolved = TranslationTargetLanguage.fromCode(prefCode)
        val targetIso = resolved.fallbackIso
        val isChineseTarget = isChineseTargetIso(targetIso)
        val isTraditionalTarget = isChineseTarget && (
            prefCode == "zh-TW" ||
                resolved == TranslationTargetLanguage.ZH_TW ||
                aiPreferences.isTraditionalChinese
            )
        return ProseTranslationSpec(targetIso, isChineseTarget, isTraditionalTarget)
    }

    private fun finalizeTranslatedProse(
        rawTranslation: String?,
        sourceText: String,
        spec: ProseTranslationSpec
    ): String? {
        if (!isValidTranslatedText(rawTranslation, sourceText, spec.targetIso)) return null
        val cleaned = HtmlUtils.cleanTranslationOutput(rawTranslation!!)
        return if (spec.isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
    }

    private fun needsProseTranslation(
        sourceText: String,
        existingTranslation: String?,
        spec: ProseTranslationSpec
    ): Boolean {
        if (sourceText.isBlank()) return false
        if (spec.isChineseTarget && AiAnnotationCurator.isAlreadyChinese(sourceText)) return false
        return existingTranslation.isNullOrBlank() ||
            !isValidTranslatedText(existingTranslation, sourceText, spec.targetIso)
    }

    private fun mergeAnnotationsPreservingProgress(
        baseMatched: Map<Int, LyricAnnotationEntity>,
        liveMap: Map<Int, LyricAnnotationEntity>?
    ): Map<Int, LyricAnnotationEntity> {
        if (liveMap.isNullOrEmpty()) return baseMatched
        return baseMatched.mapValues { (lineIdx, item) ->
            val existing = liveMap[lineIdx]
            if (existing != null &&
                existing.lyricFragment == item.lyricFragment &&
                existing.explanationText == item.explanationText
            ) {
                item.copy(
                    lyricTranslation = item.lyricTranslation ?: existing.lyricTranslation,
                    explanationTranslation = if (!item.explanationTranslation.isNullOrBlank()) {
                        item.explanationTranslation
                    } else {
                        existing.explanationTranslation
                    }
                )
            } else {
                item
            }
        }
    }

    private fun mergeStoryPreservingProgress(
        candidate: SongStoryEntity?,
        existing: SongStoryEntity?
    ): SongStoryEntity? {
        if (candidate == null) return existing
        if (existing != null &&
            existing.descriptionPlain == candidate.descriptionPlain &&
            candidate.descriptionTranslation.isNullOrBlank() &&
            !existing.descriptionTranslation.isNullOrBlank()
        ) {
            return candidate.copy(descriptionTranslation = existing.descriptionTranslation)
        }
        return candidate
    }

    private fun publishGeniusTranslationProgress(
        trackEpoch: Long,
        cacheKey: String,
        lyrics: List<BilingualLyricLine>,
        story: SongStoryEntity?,
        annotations: List<LyricAnnotationEntity>,
        failureNotice: String?
    ) {
        val rawMatched = if (lyrics.isNotEmpty() && annotations.isNotEmpty()) {
            LyricFragmentMatcher.matchAnnotationsToLines(lyrics, annotations)
        } else {
            emptyMap()
        }

        if (lyrics.isNotEmpty()) {
            synchronized(memoryCache) {
                val existing = memoryCache[cacheKey]
                val mergedMatched = mergeAnnotationsPreservingProgress(rawMatched, existing?.annotatedLines)
                val mergedStory = mergeStoryPreservingProgress(story, existing?.songStory)
                putMemoryCacheSafely(
                    cacheKey,
                    CachedSongData(
                        lyrics = existing?.lyrics?.takeIf { it.isNotEmpty() } ?: lyrics,
                        annotatedLines = if (mergedMatched.isNotEmpty()) mergedMatched else (existing?.annotatedLines ?: emptyMap()),
                        songStory = mergedStory,
                        geniusNotice = failureNotice ?: existing?.geniusNotice,
                        hasAttemptedGenius = true
                    )
                )
            }
        }

        if (trackEpoch == activeTrackEpoch) {
            _lyricsContentFlow.update { current ->
                if (current.trackEpoch != trackEpoch) return@update current
                val mergedMatched = mergeAnnotationsPreservingProgress(rawMatched, current.annotatedLines)
                val mergedStory = mergeStoryPreservingProgress(story, current.songStory)
                current.copy(
                    annotatedLines = if (mergedMatched.isNotEmpty()) mergedMatched else current.annotatedLines,
                    songStory = mergedStory ?: current.songStory
                )
            }
            syncNowPlayingAggregate(content = _lyricsContentFlow.value)
        }
    }

    private suspend fun translateStoryAndAnnotations(
        initialStory: SongStoryEntity?,
        initialAnnotations: List<LyricAnnotationEntity>,
        spec: ProseTranslationSpec,
        prioritizeInteractive: Boolean,
        onProgress: (suspend (SongStoryEntity?, List<LyricAnnotationEntity>) -> Unit)? = null
    ): Pair<SongStoryEntity?, List<LyricAnnotationEntity>> = coroutineScope {
        var latestStory = initialStory
        val latestAnnotations = initialAnnotations.toMutableList()
        val emitMutex = Mutex()

        val storyJob = async {
            val s = initialStory
            if (s != null && needsProseTranslation(s.descriptionPlain, s.descriptionTranslation, spec)) {
                try {
                    val tr = translationService.translateText(
                        text = s.descriptionPlain,
                        targetIso = spec.targetIso,
                        highPriority = prioritizeInteractive
                    )
                    val finalized = finalizeTranslatedProse(tr, s.descriptionPlain, spec)
                    if (finalized != null) {
                        val updatedStory = s.copy(descriptionTranslation = finalized)
                        emitMutex.withLock {
                            latestStory = updatedStory
                            onProgress?.invoke(latestStory, latestAnnotations.toList())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }

        val pendingIndices = latestAnnotations.indices.filter { idx ->
            needsProseTranslation(
                latestAnnotations[idx].explanationText,
                latestAnnotations[idx].explanationTranslation,
                spec
            )
        }

        val annotationsJob = async {
            if (pendingIndices.isEmpty()) return@async
            val batchChunks = pendingIndices.chunked(6)
            val batchSemaphore = Semaphore(if (prioritizeInteractive) 3 else 2)

            batchChunks.map { idxGroup ->
                async {
                    batchSemaphore.withPermit {
                        try {
                            val inputs = idxGroup.map { latestAnnotations[it].explanationText }
                            val outputs = translationService.translateTextsBatch(
                                texts = inputs,
                                targetIso = spec.targetIso,
                                highPriority = false
                            )
                            var changedCount = 0
                            emitMutex.withLock {
                                idxGroup.forEachIndexed { pos, origIdx ->
                                    val origAnnot = latestAnnotations[origIdx]
                                    val finalized = finalizeTranslatedProse(outputs.getOrNull(pos), origAnnot.explanationText, spec)
                                    if (finalized != null) {
                                        latestAnnotations[origIdx] = origAnnot.copy(explanationTranslation = finalized)
                                        changedCount++
                                    }
                                }
                                if (changedCount > 0) {
                                    onProgress?.invoke(latestStory, latestAnnotations.toList())
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
            }.awaitAll()
        }

        storyJob.await()
        annotationsJob.await()
        latestStory to latestAnnotations.toList()
    }

    init {
        // 1. 监听 Spotify 播放状态变更
        repositoryScope.launch {
            playbackStateManager.playbackState.collect { state ->
                handlePlaybackStateUpdate(state)
            }
        }

        // 2. 监听歌词源与目标翻译语种变更，按新组合键优先命中缓存或拉取新数据
        repositoryScope.launch {
            combine(aiPreferences.lyricsSourceFlow, aiPreferences.targetLanguageFlow) { source, target ->
                "$source|$target"
            }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val state = playbackStateManager.playbackState.value
                    val songKey = currentSongKey
                    if (state.hasValidTrack && songKey != null) {
                        loadSongLyricsAndGenius(state.title, state.artist, songKey, activeTrackEpoch)
                    }
                }
        }

        // 3. 毫秒级时间轴驱动与高亮行定位 (60fps 级流畅推算)
        startPositionTracker()

        // 4. 初始载入全局歌词时间轴偏移 (默认 0ms 原生自然时间轴)
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
            lastPreloadTriggerKey = null
            dataLoadJob?.cancel()
            trackLoadDebounceJob?.cancel()
            preloadingJob?.cancel()
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
            lastPreloadTriggerKey = null
            lastAutoRetryTimeMs = SystemClock.elapsedRealtime()
            dataLoadJob?.cancel()
            trackLoadDebounceJob?.cancel()
            noticeDismissJob?.cancel()
            preloadingJob?.cancel()

            val songOffset = floatingPreferences.getSongOffset(songKey)
            currentSongOffsetMs = songOffset
            _songOffsetMsFlow.value = songOffset
            updateEffectiveOffset()

            // 切歌瞬间原子清空上一曲歌词、典故、故事與失敗提示，徹底消除切歌殘影；
            // 若命中完整缓存则秒开，若仅译文残缺且未受报错词条污染，先行保留原文与已加载 Genius 典故并平滑补全译文
            val cached = memoryCache[buildCacheKey(songKey)]
            val isCachedIncomplete = isCacheTranslationIncomplete(cached)
            val hasCorruptedInMem = cached?.lyrics?.any { LyricAligner.isInvalidTranslationLine(it.translation) } == true
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
                val canKeepPartialLyrics = cached != null && cached.lyrics.isNotEmpty() && !hasCorruptedInMem
                val initialLyrics = if (canKeepPartialLyrics) cached!!.lyrics else emptyList()
                val initialPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                val initialIdx = if (initialLyrics.isNotEmpty()) calculateActiveLineIndex(initialLyrics, initialPos) else -1
                val resetContent = LyricsContentState(
                    trackEpoch = state.trackEpoch,
                    lyrics = initialLyrics,
                    annotatedLines = if (canKeepPartialLyrics) cached!!.annotatedLines else emptyMap(),
                    songStory = if (canKeepPartialLyrics) cached!!.songStory else null,
                    isLoadingLyrics = true,
                    isLoadingGenius = !(canKeepPartialLyrics && cached!!.hasAttemptedGenius),
                    geniusNoticeMessage = if (canKeepPartialLyrics) cached!!.geniusNotice else null,
                    errorMessage = null
                )
                _lyricsContentFlow.value = resetContent
                _currentLineIndexFlow.value = initialIdx
                syncNowPlayingAggregate(playbackState = state, content = resetContent, lineIndex = initialIdx)

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

        // 仅在实质切歌或队列曲目变更时触发预加载，队列清空时立即取消残留预加载任务以防反向覆盖当前曲目
        if (state.queueItems.isNotEmpty()) {
            val queueSig = state.queueItems.joinToString("|") { "${it.artist}:${it.title}" }
            val triggerKey = "$songKey||$queueSig"
            if (triggerKey != lastPreloadTriggerKey) {
                lastPreloadTriggerKey = triggerKey
                preloadNextTracks(state.queueItems)
            }
        } else {
            preloadingJob?.cancel()
            lastPreloadTriggerKey = null
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = repositoryScope.launch {
            combine(
                LinerNotesApp.isForegroundFlow,
                FloatingLyricsService.isOverlayVisibleFlow,
                _lyricsContentFlow.map { it.lyrics }.distinctUntilChanged(),
                playbackClockFlow,
                _lyricOffsetMsFlow
            ) { appInForeground, floatingVisible, lyrics, clock, offsetMs ->
                TrackerInput(
                    isObserverActive = appInForeground || floatingVisible,
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
            (((nextTriggerMs - currentPosMs) / playbackSpeed).toLong() + 4L).coerceIn(5L, 450L)
        } else {
            500L
        }
    }

    private fun isCacheTranslationIncomplete(cached: CachedSongData?): Boolean {
        if (cached == null || cached.lyrics.isEmpty()) return false
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        return isTranslationIncomplete(cached.lyrics, targetIso)
    }

    /**
     * 安全写入内存缓存：绝不允许译文残缺或缺失的后台预加载/兜底结果覆盖已有的完整双语缓存，
     * 同时允许无损合并新拉取到的 Genius 典故与背景故事。
     */
    private fun putMemoryCacheSafely(cacheKey: String, candidate: CachedSongData) {
        synchronized(memoryCache) {
            val existing = memoryCache[cacheKey]
            val merged = if (existing != null && existing.lyrics.isNotEmpty() &&
                !isCacheTranslationIncomplete(existing) &&
                isCacheTranslationIncomplete(candidate)
            ) {
                candidate.copy(
                    lyrics = existing.lyrics,
                    annotatedLines = if (candidate.annotatedLines.isNotEmpty()) candidate.annotatedLines else existing.annotatedLines,
                    songStory = candidate.songStory ?: existing.songStory,
                    geniusNotice = candidate.geniusNotice ?: existing.geniusNotice,
                    hasAttemptedGenius = candidate.hasAttemptedGenius || existing.hasAttemptedGenius
                )
            } else {
                candidate
            }
            memoryCache[cacheKey] = merged
        }
    }

    private fun buildLrcFromLines(lines: List<BilingualLyricLine>): String {
        return lines.joinToString("\n") { line ->
            if (line.isStanzaBreak) {
                ""
            } else if (line.startTimeMs != null) {
                "${LyricAligner.formatTimestamp(line.startTimeMs)} ${line.original}".trimEnd()
            } else {
                line.original
            }
        }
    }

    /**
     * 统一的歌词双语化与脱敏对齐管线（主加载、队列预加载、手动选源共用）：
     * 1. 先清理被配额告警或目标语种错配污染的译文；
     * 2. 若需中文译文但原源缺翻或残缺，优先从网易云/QQ音乐官方精翻库补全（可选同步替换同源时间轴）；
     * 3. 若仍缺译文或非中目标语种，调用机器翻译并严格校验至少产出一行真实译文，绝不接受纯时间戳伪非空结果。
     */
    private suspend fun resolveAlignedBilingualLyrics(
        title: String,
        artist: String,
        initialOrigLyrics: String,
        initialTransLyrics: String?,
        durationMs: Long,
        allowReplaceOriginWithOfficial: Boolean
    ): List<BilingualLyricLine> {
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isChineseTarget = isChineseTargetIso(targetIso)
        val isTraditionalTarget = isChineseTarget && (
            aiPreferences.targetLanguage == "zh-TW" ||
                TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW ||
                aiPreferences.isTraditionalChinese
            )

        var origLyrics = initialOrigLyrics
        val isOrigAlreadyChinese = AiAnnotationCurator.isAlreadyChinese(origLyrics)
        var transLyrics = initialTransLyrics

        if (isChineseTarget && isOrigAlreadyChinese) {
            transLyrics = null
        }
        if (transLyrics != null && (LyricAligner.isRefusalText(transLyrics) || transLyrics.lines().any { LyricAligner.isInvalidTranslationLine(it) })) {
            transLyrics = null
        }
        if (!isChineseTarget && transLyrics != null && CJK_REGEX.containsMatchIn(transLyrics)) {
            transLyrics = null
        }

        if (isChineseTarget && !isOrigAlreadyChinese && isRawTranslationLacking(origLyrics, transLyrics)) {
            try {
                val officialBilingual = NetEaseLyricsService.fetchLyrics(title, artist, durationMs)
                    ?.takeIf { !isRawTranslationLacking(it.originalLyrics, it.translatedLyrics) }
                    ?: QQMusicLyricsService.fetchLyrics(title, artist, durationMs)
                        ?.takeIf { !isRawTranslationLacking(it.originalLyrics, it.translatedLyrics) }
                if (officialBilingual != null && !officialBilingual.translatedLyrics.isNullOrBlank()) {
                    // 关键同源对齐保护：仅当允许切换数据源且官方双语源自带 LRC 时间轴时，将原文与译文一并切为同源版本，
                    // 严禁把 LRCLIB 断句原文与网易云/QQ 译文强行混拼！
                    if (allowReplaceOriginWithOfficial && (
                            officialBilingual.originalLyrics.contains(LyricAligner.LRC_TIMESTAMP_REGEX) ||
                                !origLyrics.contains(LyricAligner.LRC_TIMESTAMP_REGEX)
                            )
                    ) {
                        origLyrics = officialBilingual.originalLyrics
                        transLyrics = officialBilingual.translatedLyrics
                    } else if (!allowReplaceOriginWithOfficial) {
                        transLyrics = officialBilingual.translatedLyrics
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        if (!(isChineseTarget && isOrigAlreadyChinese) && isRawTranslationLacking(origLyrics, transLyrics)) {
            try {
                val transResult = translationService.translateTrack(title, origLyrics)
                val candidateTrans = transResult.translatedLyrics
                val candCount = countNonBlankLrcLines(candidateTrans)
                val curCount = countNonBlankLrcLines(transLyrics)
                if (candCount > 0 &&
                    candCount >= curCount &&
                    !LyricAligner.isRefusalText(candidateTrans) &&
                    candidateTrans.lines().none { LyricAligner.isInvalidTranslationLine(it) }
                ) {
                    transLyrics = candidateTrans
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        val cleanOrig = if (aiPreferences.isDeCensorEnabled) {
            LyricSanitizer.decensorLyrics(origLyrics, uncensoredRefLyrics = null, translationLyrics = transLyrics)
        } else origLyrics
        val rawCleanTrans = if (aiPreferences.isDeCensorEnabled) {
            if (isChineseTarget) LyricSanitizer.decensorChineseLyrics(transLyrics, cleanOrig) else transLyrics
        } else transLyrics
        val cleanTrans = if (isTraditionalTarget && !rawCleanTrans.isNullOrBlank()) {
            ChineseConverter.toTraditional(rawCleanTrans)
        } else rawCleanTrans

        return LyricAligner.align(cleanOrig, cleanTrans)
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
        val cacheKey = buildCacheKey(songKey)
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            // 清理歌词缓存但保留已抓取的 Genius 背景故事，防止重复拉取 Genius
            memoryCache[cacheKey] = cached.copy(lyrics = emptyList(), annotatedLines = emptyMap())
        } else {
            memoryCache.remove(cacheKey)
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
        trackLoadDebounceJob?.cancel()
        dataLoadJob?.cancel()
        noticeDismissJob?.cancel()

        val cacheKey = buildCacheKey(songKey)
        val cached = memoryCache[cacheKey]
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

            // 若上次切歌打断了阶段二翻译或仍有缺失翻译的背景故事/典故，后台无缝补翻并刷新
            val proseSpec = currentProseTranslationSpec()
            val distinctCachedAnnots = cached.annotatedLines.values.distinctBy { "${it.lyricFragment}|${it.explanationText}" }
            val storyNeedsTrans = cached.songStory?.let {
                needsProseTranslation(it.descriptionPlain, it.descriptionTranslation, proseSpec)
            } == true
            val annotsNeedTrans = distinctCachedAnnots.any {
                needsProseTranslation(it.explanationText, it.explanationTranslation, proseSpec)
            }
            if (storyNeedsTrans || annotsNeedTrans) {
                dataLoadJob = repositoryScope.launch {
                    if (trackEpoch != activeTrackEpoch) return@launch
                    val (finalStory, finalAnnots) = translateStoryAndAnnotations(
                        initialStory = cached.songStory,
                        initialAnnotations = distinctCachedAnnots,
                        spec = proseSpec,
                        prioritizeInteractive = true,
                        onProgress = { progStory, progAnnots ->
                            publishGeniusTranslationProgress(
                                trackEpoch = trackEpoch,
                                cacheKey = cacheKey,
                                lyrics = safeLyrics,
                                story = progStory,
                                annotations = progAnnots,
                                failureNotice = cached.geniusNotice
                            )
                        }
                    )
                    publishGeniusTranslationProgress(
                        trackEpoch = trackEpoch,
                        cacheKey = cacheKey,
                        lyrics = safeLyrics,
                        story = finalStory,
                        annotations = finalAnnots,
                        failureNotice = cached.geniusNotice
                    )
                }
            }
            return
        }

        val canKeepPartialLyrics = cached != null && isCachedTranslationSeverelyIncomplete &&
            cached.lyrics.isNotEmpty() &&
            cached.lyrics.none { LyricAligner.isInvalidTranslationLine(it.translation) }
        val hasReusableGeniusCache = cached != null && cached.hasAttemptedGenius &&
            cached.lyrics.none { LyricAligner.isInvalidTranslationLine(it.translation) } &&
            (cached.annotatedLines.isNotEmpty() || cached.songStory != null)

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
            val keepLyrics = if (canKeepPartialLyrics) cached!!.lyrics else emptyList()
            val curPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            val keepIdx = if (keepLyrics.isNotEmpty()) calculateActiveLineIndex(keepLyrics, curPos) else -1
            val content = LyricsContentState(
                trackEpoch = trackEpoch,
                lyrics = keepLyrics,
                annotatedLines = if (hasReusableGeniusCache) cached!!.annotatedLines else emptyMap(),
                songStory = cached?.songStory,
                isLoadingLyrics = true,
                isLoadingGenius = !hasReusableGeniusCache,
                geniusNoticeMessage = cached?.geniusNotice,
                errorMessage = null
            )
            _lyricsContentFlow.value = content
            _currentLineIndexFlow.value = keepIdx
            syncNowPlayingAggregate(content = content, lineIndex = keepIdx)
        }

        dataLoadJob = repositoryScope.launch {
            if (trackEpoch != activeTrackEpoch) return@launch
            val hasCorruptedTranslation = cached?.lyrics?.any { LyricAligner.isInvalidTranslationLine(it.translation) } == true
            var fetchedLyrics: List<BilingualLyricLine> = if (hasCorruptedTranslation || isCachedTranslationSeverelyIncomplete) emptyList() else (cached?.lyrics ?: emptyList())
            var fetchedStory: SongStoryEntity? = cached?.songStory
            var fetchedAnnotations: List<LyricAnnotationEntity> = if (hasReusableGeniusCache) {
                cached!!.annotatedLines.values.distinctBy { "${it.lyricFragment}|${it.explanationText}" }
            } else emptyList()

            // 1. 若歌词尚未就绪或仅需补全缺失译文，并发抓取完整歌词与自动双语补全
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
                        fetchedLyrics = resolveAlignedBilingualLyrics(
                            title = title,
                            artist = artist,
                            initialOrigLyrics = rawResult.originalLyrics,
                            initialTransLyrics = rawResult.translatedLyrics,
                            durationMs = curState.durationMs,
                            allowReplaceOriginWithOfficial = manualBinding == null
                        )
                    } else if (canKeepPartialLyrics) {
                        // 网络重抓兜底失败时，直接复用缓存原文时间轴执行双语补全
                        val origFromCache = buildLrcFromLines(cached!!.lyrics)
                        if (origFromCache.isNotBlank()) {
                            fetchedLyrics = resolveAlignedBilingualLyrics(
                                title = title,
                                artist = artist,
                                initialOrigLyrics = origFromCache,
                                initialTransLyrics = null,
                                durationMs = curState.durationMs,
                                allowReplaceOriginWithOfficial = manualBinding == null
                            ).ifEmpty { cached.lyrics }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 并发抓取 Genius 歌曲创作背景与歌词行典故
            var geniusSongFound = hasReusableGeniusCache
            var currentSongId: Long? = null
            val geniusJob = async {
                if (hasReusableGeniusCache) return@async
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
                } catch (e: CancellationException) {
                    throw e
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
                } catch (e: CancellationException) {
                    throw e
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
                !GeniusService.lastRequestConnected -> NowPlayingMessageKeys.GENIUS_NETWORK_ERROR
                !geniusSongFound -> NowPlayingMessageKeys.GENIUS_NOT_FOUND
                fetchedAnnotations.isEmpty() -> NowPlayingMessageKeys.GENIUS_NO_ANNOTATIONS
                else -> NowPlayingMessageKeys.GENIUS_MISMATCH
            }

            if (fetchedLyrics.isNotEmpty()) {
                val preliminaryData = CachedSongData(
                    lyrics = fetchedLyrics,
                    annotatedLines = initialMatched,
                    songStory = fetchedStory,
                    geniusNotice = failureNotice,
                    hasAttemptedGenius = true
                )
                putMemoryCacheSafely(cacheKey, preliminaryData)
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

            // 阶段二：背景故事走高优快车道独立先出 + 典故批量打包翻译（渐进实时刷新，互不阻塞）
            if (fetchedStory != null || fetchedAnnotations.isNotEmpty()) {
                val proseSpec = currentProseTranslationSpec()
                val (finalStory, finalAnnots) = translateStoryAndAnnotations(
                    initialStory = fetchedStory,
                    initialAnnotations = fetchedAnnotations,
                    spec = proseSpec,
                    prioritizeInteractive = true,
                    onProgress = { progStory, progAnnots ->
                        publishGeniusTranslationProgress(
                            trackEpoch = trackEpoch,
                            cacheKey = cacheKey,
                            lyrics = fetchedLyrics,
                            story = progStory,
                            annotations = progAnnots,
                            failureNotice = failureNotice
                        )
                    }
                )
                fetchedStory = finalStory
                fetchedAnnotations = finalAnnots

                if (trackEpoch == activeTrackEpoch) {
                    publishGeniusTranslationProgress(
                        trackEpoch = trackEpoch,
                        cacheKey = cacheKey,
                        lyrics = fetchedLyrics,
                        story = fetchedStory,
                        annotations = fetchedAnnotations,
                        failureNotice = failureNotice
                    )
                }
            }
        }
    }

    private fun preloadNextTracks(queueItems: List<com.linernotes.app.core.playback.QueueTrackItem>) {
        if (queueItems.isEmpty()) return
        preloadingJob?.cancel()
        preloadingJob = repositoryScope.launch {
            // 先等待当前歌曲的主加载与阶段二翻译全部完成，再开始静默预加载队列曲目，绝不抢占交互带宽
            try {
                dataLoadJob?.join()
            } catch (_: CancellationException) {
            }
            delay(800L)
            val currentKey = currentSongKey
            // 提取待播队列中接下来的 3 首曲目进行深度静默预加载 (含缺翻/残缺缓存自愈)
            val candidates = queueItems.filter { item ->
                if (item.title.isBlank() || item.artist.isBlank()) return@filter false
                val key = "${item.artist.trim().lowercase()} - ${item.title.trim().lowercase()}"
                val cached = memoryCache[buildCacheKey(key)]
                key != currentKey && (
                    cached == null ||
                        isCacheTranslationIncomplete(cached) ||
                        (!cached.hasAttemptedGenius && cached.annotatedLines.isEmpty() && cached.songStory == null)
                    )
            }.take(3)

            for (item in candidates) {
                val nextKey = "${item.artist.trim().lowercase()} - ${item.title.trim().lowercase()}"
                if (nextKey == currentSongKey) continue
                try {
                    silentFetchAndCacheSongFull(item.title, item.artist, nextKey)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 静默容灾
                }
            }
        }
    }

    private suspend fun silentFetchAndCacheSongFull(title: String, artist: String, songKey: String) {
        if (currentSongKey == songKey) return
        val cacheKey = buildCacheKey(songKey)
        val cachedInMem = memoryCache[cacheKey]
        val isCachedTranslationSeverelyIncomplete = isCacheTranslationIncomplete(cachedInMem)

        var lyrics = if (!isCachedTranslationSeverelyIncomplete) cachedInMem?.lyrics ?: emptyList() else emptyList()

        if (lyrics.isEmpty()) {
            val rawResult = UnifiedLyricsService.fetchLyrics(
                trackTitle = title,
                artistName = artist,
                sourcePref = aiPreferences.lyricsSource
            )

            val matchedRaw = if (rawResult != null && rawResult.originalLyrics.isNotBlank()) {
                val matchScore = LyricSearchCleaner.scoreCandidateMatch(
                    candidateTitle = rawResult.title,
                    candidateArtist = rawResult.artist,
                    targetTitle = title,
                    targetArtist = artist
                )
                if (matchScore >= 50) rawResult else null
            } else null

            if (currentSongKey == songKey) return

            if (matchedRaw != null) {
                lyrics = resolveAlignedBilingualLyrics(
                    title = title,
                    artist = artist,
                    initialOrigLyrics = matchedRaw.originalLyrics,
                    initialTransLyrics = matchedRaw.translatedLyrics,
                    durationMs = matchedRaw.durationMs,
                    allowReplaceOriginWithOfficial = true
                )
            } else if (cachedInMem != null && isCachedTranslationSeverelyIncomplete && cachedInMem.lyrics.isNotEmpty()) {
                val origFromCache = buildLrcFromLines(cachedInMem.lyrics)
                if (origFromCache.isNotBlank()) {
                    lyrics = resolveAlignedBilingualLyrics(
                        title = title,
                        artist = artist,
                        initialOrigLyrics = origFromCache,
                        initialTransLyrics = null,
                        durationMs = 0L,
                        allowReplaceOriginWithOfficial = true
                    ).ifEmpty { cachedInMem.lyrics }
                }
            } else {
                return
            }
        }

        if (lyrics.isEmpty() || currentSongKey == songKey) return

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

                // 批量打包极速翻译（与前台阶段二共享同一批量引擎）
                val proseSpec = currentProseTranslationSpec()
                val (translatedStory, translatedAnnots) = translateStoryAndAnnotations(
                    initialStory = fetchedStory,
                    initialAnnotations = fetchedAnnotations,
                    spec = proseSpec,
                    prioritizeInteractive = false
                )
                fetchedStory = translatedStory
                fetchedAnnotations = translatedAnnots
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 静默容灾
        }

        // 3. 典故行级精准匹配
        val matchedAnnotations = if (lyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
            LyricFragmentMatcher.matchAnnotationsToLines(lyrics, fetchedAnnotations)
        } else {
            emptyMap()
        }

        // 4. 安全存入内存缓存（不覆盖已有完整双语缓存）
        val cached = CachedSongData(
            lyrics = lyrics,
            annotatedLines = matchedAnnotations,
            songStory = fetchedStory,
            geniusNotice = null,
            hasAttemptedGenius = true
        )
        putMemoryCacheSafely(cacheKey, cached)

        // 若切歌竞态下当前歌曲恰好变成此曲且主加载未激活，仅在未降级现有翻译时才回填界面
        if (currentSongKey == songKey && dataLoadJob?.isActive != true) {
            val activeContent = _lyricsContentFlow.value
            val targetIsoNow = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
            val activeHasBetterLyrics = activeContent.lyrics.isNotEmpty() &&
                !isTranslationIncomplete(activeContent.lyrics, targetIsoNow) &&
                isTranslationIncomplete(lyrics, targetIsoNow)
            if (!activeHasBetterLyrics) {
                val curPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                val lineIdx = calculateActiveLineIndex(lyrics, curPos)
                _lyricsContentFlow.update {
                    it.copy(
                        lyrics = lyrics,
                        annotatedLines = if (matchedAnnotations.isNotEmpty()) matchedAnnotations else it.annotatedLines,
                        songStory = fetchedStory ?: it.songStory,
                        isLoadingLyrics = false,
                        isLoadingGenius = false
                    )
                }
                _currentLineIndexFlow.value = lineIdx
                syncNowPlayingAggregate(content = _lyricsContentFlow.value, lineIndex = lineIdx)
            }
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
        val cacheKey = buildCacheKey(songKey)
        val existingLyrics = memoryCache[cacheKey]?.lyrics ?: content.lyrics

        // 立即进入加载中状态并更新通知，向用户提供即时正反馈
        val loadingNotice = NowPlayingMessageKeys.GENIUS_SEARCHING
        _lyricsContentFlow.update {
            it.copy(
                isLoadingGenius = true,
                geniusNoticeMessage = loadingNotice
            )
        }
        syncNowPlayingAggregate(content = _lyricsContentFlow.value)

        if (existingLyrics.isNotEmpty()) {
            memoryCache[cacheKey] = CachedSongData(
                lyrics = existingLyrics,
                annotatedLines = emptyMap(),
                songStory = null,
                geniusNotice = loadingNotice,
                hasAttemptedGenius = false
            )
        } else {
            memoryCache.remove(cacheKey)
        }
        loadSongLyricsAndGenius(state.title, state.artist, songKey, state.trackEpoch)
    }

    suspend fun translateSingleAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        val initialSongKey = currentSongKey
        val initialEpoch = activeTrackEpoch
        val spec = currentProseTranslationSpec()
        var updated = annotation

        val needLyric = updated.lyricTranslation.isNullOrBlank() &&
            !(spec.isChineseTarget && AiAnnotationCurator.isAlreadyChinese(updated.lyricFragment))
        val needExpl = !(spec.isChineseTarget && AiAnnotationCurator.isAlreadyChinese(updated.explanationText))

        val batchInputs = mutableListOf<String>()
        val lyricIdx = if (needLyric) batchInputs.size.also { batchInputs.add(updated.lyricFragment) } else -1
        val explIdx = if (needExpl) batchInputs.size.also { batchInputs.add(updated.explanationText) } else -1

        if (batchInputs.isNotEmpty()) {
            val batchOutputs = translationService.translateTextsBatch(
                texts = batchInputs,
                targetIso = spec.targetIso,
                highPriority = true
            )
            if (lyricIdx >= 0) {
                finalizeTranslatedProse(batchOutputs.getOrNull(lyricIdx), updated.lyricFragment, spec)?.let {
                    updated = updated.copy(lyricTranslation = it)
                }
            }
            if (explIdx >= 0) {
                finalizeTranslatedProse(batchOutputs.getOrNull(explIdx), updated.explanationText, spec)?.let {
                    updated = updated.copy(explanationTranslation = it)
                }
            }
        }

        if (updated != annotation) {
            val replaceMatchingAnnotation: (Map<Int, LyricAnnotationEntity>) -> Map<Int, LyricAnnotationEntity> = { map ->
                map.mapValues { (_, v) ->
                    val matchesId = annotation.id > 0L && v.id == annotation.id
                    val matchesContent = v.lyricFragment == annotation.lyricFragment && v.explanationText == annotation.explanationText
                    if (matchesId || matchesContent) updated else v
                }
            }
            if (initialSongKey != null) {
                val cKey = buildCacheKey(initialSongKey)
                memoryCache[cKey]?.let { cached ->
                    memoryCache[cKey] = cached.copy(
                        annotatedLines = replaceMatchingAnnotation(cached.annotatedLines)
                    )
                }
            }
            if (currentSongKey == initialSongKey && activeTrackEpoch == initialEpoch) {
                _lyricsContentFlow.update { current ->
                    current.copy(annotatedLines = replaceMatchingAnnotation(current.annotatedLines))
                }
                syncNowPlayingAggregate(content = _lyricsContentFlow.value)
            }
        }
        return@withContext updated
    }

    suspend fun translateCurrentStory(story: SongStoryEntity): SongStoryEntity = withContext(Dispatchers.IO) {
        val initialSongKey = currentSongKey
        val initialEpoch = activeTrackEpoch
        val spec = currentProseTranslationSpec()
        if (spec.isChineseTarget && AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
            return@withContext story
        }

        val tr = translationService.translateText(
            text = story.descriptionPlain,
            targetIso = spec.targetIso,
            highPriority = true
        )
        val finalTr = finalizeTranslatedProse(tr, story.descriptionPlain, spec)
        if (finalTr != null) {
            val updated = story.copy(descriptionTranslation = finalTr)

            if (initialSongKey != null) {
                val cKey = buildCacheKey(initialSongKey)
                memoryCache[cKey]?.let { cached ->
                    memoryCache[cKey] = cached.copy(songStory = updated)
                }
            }
            if (currentSongKey == initialSongKey && activeTrackEpoch == initialEpoch) {
                _lyricsContentFlow.update { it.copy(songStory = updated) }
                syncNowPlayingAggregate(content = _lyricsContentFlow.value)
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
            val curDuration = playbackStateManager.playbackState.value.durationMs
            val aligned = resolveAlignedBilingualLyrics(
                title = trackTitle,
                artist = artistName,
                initialOrigLyrics = rawResult.originalLyrics,
                initialTransLyrics = rawResult.translatedLyrics,
                durationMs = curDuration,
                allowReplaceOriginWithOfficial = false
            )
            if (aligned.isNotEmpty()) {
                val songKey = "${artistName.trim().lowercase()} - ${trackTitle.trim().lowercase()}"
                if (currentSongKey == songKey) {
                    trackLoadDebounceJob?.cancel()
                    dataLoadJob?.cancel()
                }
                val cacheKey = buildCacheKey(songKey)
                val prevContent = _lyricsContentFlow.value
                val prevCache = memoryCache[cacheKey]
                val prevAnnotEntities = (prevCache?.annotatedLines ?: prevContent.annotatedLines)
                    .values
                    .distinctBy { "${it.lyricFragment}|${it.explanationText}" }
                val rematchedAnnotations = if (prevAnnotEntities.isNotEmpty()) {
                    LyricFragmentMatcher.matchAnnotationsToLines(aligned, prevAnnotEntities)
                } else {
                    emptyMap()
                }
                val updatedCache = CachedSongData(
                    lyrics = aligned,
                    annotatedLines = rematchedAnnotations,
                    songStory = prevCache?.songStory ?: prevContent.songStory,
                    geniusNotice = prevCache?.geniusNotice ?: prevContent.geniusNoticeMessage,
                    hasAttemptedGenius = prevCache?.hasAttemptedGenius ?: false
                )
                memoryCache[cacheKey] = updatedCache
                if (currentSongKey == songKey) {
                    val curPos = (playbackClockFlow.value.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                    val lineIdx = calculateActiveLineIndex(aligned, curPos)
                    _lyricsContentFlow.update {
                        it.copy(
                            lyrics = aligned,
                            annotatedLines = rematchedAnnotations,
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

        internal fun countNonBlankLrcLines(lrc: String?): Int {
            if (lrc.isNullOrBlank()) return 0
            return lrc.lines().count { line ->
                val text = LyricAligner.cleanLine(line)
                text.isNotBlank() && text.any { it.isLetterOrDigit() }
            }
        }

        internal fun isRawTranslationLacking(origLyrics: String, transLyrics: String?): Boolean {
            val origCount = countNonBlankLrcLines(origLyrics)
            if (origCount == 0) return false
            if (transLyrics.isNullOrBlank() ||
                LyricAligner.isRefusalText(transLyrics) ||
                transLyrics.lines().any { LyricAligner.isInvalidTranslationLine(it) }
            ) {
                return true
            }
            val transCount = countNonBlankLrcLines(transLyrics)
            return transCount == 0 || (origCount > 3 && transCount * 2 < origCount)
        }

        internal fun isTranslationIncomplete(lyrics: List<BilingualLyricLine>, targetIso: String): Boolean {
            if (lyrics.isEmpty()) return false
            val lyricLines = lyrics.filter { !it.isStanzaBreak && it.original.isNotBlank() }
            if (lyricLines.isEmpty()) return false
            if (lyricLines.any { LyricAligner.isInvalidTranslationLine(it.translation) }) return true

            val contentLines = lyricLines.filter { line -> line.original.any { it.isLetter() } }
            if (contentLines.isEmpty()) return false

            if (targetIso.startsWith("zh", ignoreCase = true)) {
                val combinedOriginal = contentLines.joinToString("\n") { it.original }
                if (AiAnnotationCurator.isAlreadyChinese(combinedOriginal)) {
                    return false
                }
            }

            val validTranslatedCount = contentLines.count {
                it.translation.isNotBlank() && !LyricAligner.isInvalidTranslationLine(it.translation)
            }
            return validTranslatedCount == 0 || (contentLines.size > 3 && validTranslatedCount * 2 < contentLines.size)
        }
    }
}

