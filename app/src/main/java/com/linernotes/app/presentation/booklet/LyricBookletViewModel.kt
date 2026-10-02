package com.linernotes.app.presentation.booklet

import android.bluetooth.BluetoothDevice
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSanitizer
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.TranslationService
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.domain.repository.AlbumRepository
import com.linernotes.app.presentation.booklet.model.BookletUiState
import com.linernotes.app.core.translation.BatchTranslationManager
import com.linernotes.app.core.translation.BatchTranslationState
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.core.bluetooth.ShanlingBluetoothManager
import com.linernotes.app.core.lyric.LrcExporter
import com.linernotes.app.data.local.dao.BookletDao
import com.linernotes.app.data.local.dao.LyricOffsetDao
import com.linernotes.app.data.local.entity.BookletPageEntity
import com.linernotes.app.data.local.entity.LyricOffsetEntity
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.remote.DiscogsService
import com.linernotes.app.presentation.booklet.components.FuriganaMode
import com.linernotes.app.core.lyric.LyricFragmentMatcher
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.repository.AnnotationRepository
import android.content.Context
import com.linernotes.app.core.media.CompanionPlaybackService
import com.linernotes.app.core.media.MediaNotificationManager
import com.linernotes.app.core.media.MediaPlaybackNotificationData
import com.linernotes.app.core.media.PlaybackCommandHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricBookletViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlbumRepository,
    val aiPreferences: AiPreferences,
    private val translationService: TranslationService,
    private val batchTranslationManager: BatchTranslationManager,
    val shanlingBluetoothManager: ShanlingBluetoothManager,
    private val lyricOffsetDao: LyricOffsetDao,
    private val bookletDao: BookletDao,
    private val annotationRepository: AnnotationRepository,
    private val mediaNotificationManager: MediaNotificationManager
) : ViewModel() {

    private var currentAlbumId: String = ""
    private var isNotificationActive: Boolean = false
    private var userTrackSelectionTimestamp = 0L
    private var userTrackSelectionLockoutMs = 3500L
    private var userSelectedTrackIndex = -1
    private var userSeekTimestamp = 0L
    private var userWantsPlay = true

    private val _uiState = MutableStateFlow(BookletUiState())
    val uiState: StateFlow<BookletUiState> = _uiState.asStateFlow()

    val batchTranslationState: StateFlow<BatchTranslationState> = batchTranslationManager.state

    val bookletPages: StateFlow<List<BookletPageEntity>> = _uiState
        .map { it.albumWithTracks?.album?.id }
        .distinctUntilChanged()
        .flatMapLatest { albumId ->
            if (albumId != null) bookletDao.getBookletPagesFlow(albumId)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun openBookletSheet(isOpen: Boolean) {
        _uiState.update { it.copy(isBookletSheetOpen = isOpen) }
    }

    fun addBookletPages(uris: List<android.net.Uri>) {
        val albumId = currentAlbumId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val currentCount = bookletDao.getPageCount(albumId)
            val newEntities = uris.mapIndexed { index, uri ->
                BookletPageEntity(
                    albumId = albumId,
                    pageNumber = currentCount + index + 1,
                    imagePath = uri.toString(),
                    pageType = "CONTENT"
                )
            }
            bookletDao.insertPages(newEntities)
            _uiState.update { it.copy(userMessage = "已成功添加 ${uris.size} 页内页画册") }
        }
    }

    fun deleteBookletPage(pageId: Long) {
        viewModelScope.launch {
            bookletDao.deletePage(pageId)
            _uiState.update { it.copy(userMessage = "已移除内页") }
        }
    }

    init {
        viewModelScope.launch {
            batchTranslationManager.state.collect { bState ->
                if (!bState.userMessage.isNullOrBlank() && bState.albumId == currentAlbumId) {
                    _uiState.update { it.copy(userMessage = bState.userMessage) }
                }
            }
        }
        viewModelScope.launch {
            shanlingBluetoothManager.cdState.collect { cdState ->
                _uiState.update {
                    it.copy(
                        cdConnectionState = cdState.connectionState,
                        cdDeviceName = cdState.deviceName,
                        cdTotalTracks = cdState.totalTracks,
                        cdCurrentTrackNumber = cdState.currentTrackNumber,
                        userMessage = cdState.errorMessage ?: it.userMessage
                    )
                }
                if (cdState.connectionState == CdConnectionState.CONNECTED && cdState.currentQueueIndex >= 0) {
                    onExternalCdStateReceived(
                        queueIndex = cdState.currentQueueIndex,
                        posMs = cdState.currentPositionMs,
                        isPlaying = cdState.isPlaying
                    )
                }
            }
        }
        viewModelScope.launch {
            annotationRepository.prewarmProgressFlow.collect { progress ->
                if (progress != null && progress.albumId == currentAlbumId) {
                    _uiState.update {
                        it.copy(
                            isAlbumPrewarming = !progress.isFinished,
                            prewarmProgress = if (progress.isFinished) null else Pair(progress.readyCount, progress.totalCount)
                        )
                    }
                } else if (progress == null) {
                    _uiState.update {
                        it.copy(isAlbumPrewarming = false, prewarmProgress = null)
                    }
                }
            }
        }
        mediaNotificationManager.commandHandler = object : PlaybackCommandHandler {
            override fun onPlay() {
                startCompanion()
            }
            override fun onPause() {
                pauseCompanion()
            }
            override fun onTogglePlay() {
                toggleCompanionPlay()
            }
            override fun onNext() {
                nextTrack()
            }
            override fun onPrevious() {
                previousTrack()
            }
            override fun onSeekTo(posMs: Long) {
                seekCompanion(posMs, notifyCdPlayer = true)
            }
        }
    }

    val allShelfAlbums: StateFlow<List<com.linernotes.app.data.local.entity.AlbumEntity>> = repository.getCollectionStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private var loadBookletJob: kotlinx.coroutines.Job? = null
    private var prefetchedAlbumId: String? = null
    private var loadAnnotationsJob: kotlinx.coroutines.Job? = null
    private var annotationObserverJob: kotlinx.coroutines.Job? = null
    private var trackSelectionJob: kotlinx.coroutines.Job? = null
    private var lastLoadedAnnotationTrackId: Long? = null
    @Volatile
    private var currentTrackRawAnnotations: List<LyricAnnotationEntity> = emptyList()
    private var companionAnchorTime = 0L
    private var companionAnchorPositionMs = 0L

    private fun rematchAnnotations(aligned: List<BilingualLyricLine>): Map<Int, LyricAnnotationEntity> {
        if (aligned.isEmpty() || currentTrackRawAnnotations.isEmpty()) return emptyMap()
        return LyricFragmentMatcher.matchAnnotationsToLines(aligned, currentTrackRawAnnotations)
    }

    private fun startObservingTrackAnnotations(trackId: Long, trackIndex: Int) {
        annotationObserverJob?.cancel()
        annotationObserverJob = viewModelScope.launch {
            combine(
                annotationRepository.getSongStoryFlow(trackId),
                annotationRepository.getAnnotationsFlow(trackId)
            ) { story, annots ->
                Pair(story, annots)
            }.collect { (story, annots) ->
                if (_uiState.value.currentTrackIndex != trackIndex) return@collect
                if (story != null || annots.isNotEmpty()) {
                    currentTrackRawAnnotations = annots
                    _uiState.update { state ->
                        val lineMap = if (annots.isNotEmpty()) {
                            LyricFragmentMatcher.matchAnnotationsToLines(state.alignedLyrics, annots)
                        } else state.lineAnnotations

                        val updatedSelected = state.selectedAnnotation?.let { sel ->
                            annots.find { it.id == sel.id } ?: sel
                        }

                        val hasContent = (story != null || lineMap.isNotEmpty() || annots.isNotEmpty())
                        state.copy(
                            songStory = story ?: state.songStory,
                            lineAnnotations = if (lineMap.isNotEmpty()) lineMap else state.lineAnnotations,
                            selectedAnnotation = updatedSelected,
                            annotationLoadState = if (hasContent) {
                                com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADED
                            } else state.annotationLoadState
                        )
                    }
                    if (isNotificationActive) {
                        updateMediaNotification()
                    }
                }
            }
        }
    }

    fun setAlbumId(id: String) {
        if (currentAlbumId != id) {
            currentAlbumId = id
            loadBooklet(id)
        }
    }

    private fun loadBooklet(id: String) {
        loadBookletJob?.cancel()
        loadBookletJob = viewModelScope.launch {
            repository.getAlbumBookletStream(id).collect { albumWithTracks ->
                if (id != currentAlbumId) return@collect
                if (albumWithTracks != null) {
                    val rawTracks = albumWithTracks.tracks.sortedBy { it.trackNumber }
                    val tracks = rawTracks.map { t ->
                        val hasOrigCensor = LyricSanitizer.hasCensorship(t.originalLyrics)
                        val hasTransCensor = LyricSanitizer.hasCensorship(t.translatedLyrics)
                        val hasTitleCensor = LyricSanitizer.hasCensorship(t.title)

                        if (hasOrigCensor || hasTransCensor || hasTitleCensor) {
                            val cleanLyrics = if (hasOrigCensor) LyricSanitizer.decensorLyrics(t.originalLyrics ?: "") else (t.originalLyrics ?: "")
                            val cleanChinese = if (hasTransCensor) LyricSanitizer.decensorChineseLyrics(t.translatedLyrics, cleanLyrics) else t.translatedLyrics
                            val cleanTitle = if (hasTitleCensor) LyricSanitizer.decensorTitle(t.title) else t.title

                            if (cleanTitle != t.title || cleanLyrics != t.originalLyrics || cleanChinese != t.translatedLyrics) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.updateTrackTranslation(t.id, t.translatedTitle, cleanLyrics, cleanChinese)
                                }
                                t.copy(title = cleanTitle, originalLyrics = cleanLyrics, translatedLyrics = cleanChinese)
                            } else {
                                t
                            }
                        } else {
                            t
                        }
                    }
                    val safeIndex = _uiState.value.currentTrackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                    val currentTrack = tracks.getOrNull(safeIndex)

                    val aligned = LyricAligner.align(
                        currentTrack?.originalLyrics,
                        currentTrack?.translatedLyrics
                    )
                    val savedOffset = currentTrack?.let { lyricOffsetDao.getOffset(it.id)?.offsetMs } ?: 0L
                    val alignedWithOffset = if (savedOffset != 0L) {
                        aligned.map { line ->
                            if (line.startTimeMs != null) {
                                line.copy(startTimeMs = (line.startTimeMs + savedOffset).coerceAtLeast(0L))
                            } else line
                        }
                    } else aligned
                    val duration = computeTrackDuration(currentTrack, alignedWithOffset)

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            albumWithTracks = albumWithTracks.copy(tracks = tracks),
                            currentTrackIndex = safeIndex,
                            alignedLyrics = alignedWithOffset,
                            lyricOffsetMs = savedOffset,
                            trackDurationMs = duration
                        )
                    }
                    val trackId = currentTrack?.id
                    if (trackId != null) {
                        if (_uiState.value.lineAnnotations.isEmpty() && _uiState.value.songStory == null) {
                            val initialStory = annotationRepository.getSongStory(trackId)
                            val initialAnnots = annotationRepository.getAnnotations(trackId)
                            if (initialStory != null || initialAnnots.isNotEmpty()) {
                                currentTrackRawAnnotations = initialAnnots
                                val lineMap = LyricFragmentMatcher.matchAnnotationsToLines(alignedWithOffset, initialAnnots)
                                _uiState.update {
                                    it.copy(
                                        songStory = initialStory,
                                        lineAnnotations = lineMap
                                    )
                                }
                            }
                        }
                        if (trackId != lastLoadedAnnotationTrackId) {
                            loadAnnotationsForCurrentTrack()
                        }
                    }
                    val otherTracks = albumWithTracks.tracks.filter { it.id != currentTrack?.id }
                    if (prefetchedAlbumId != id && otherTracks.isNotEmpty()) {
                        prefetchedAlbumId = id
                        annotationRepository.enqueueAlbumPrefetch(
                            albumId = id,
                            artist = albumWithTracks.album.artist,
                            tracks = albumWithTracks.tracks,
                            currentTrackIndex = _uiState.value.currentTrackIndex
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun setDisplayMode(mode: LyricDisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
    }

    fun cycleDisplayMode() {
        val nextMode = when (_uiState.value.displayMode) {
            LyricDisplayMode.BILINGUAL -> LyricDisplayMode.ORIGINAL_ONLY
            LyricDisplayMode.ORIGINAL_ONLY -> LyricDisplayMode.TRANSLATED_ONLY
            LyricDisplayMode.TRANSLATED_ONLY -> LyricDisplayMode.BILINGUAL
        }
        _uiState.update { it.copy(displayMode = nextMode) }
    }

    fun toggleTraditionalMode() {
        convertCurrentTrackTranslation(toTraditional = !_uiState.value.isTraditionalMode)
    }

    private fun computeTrackDuration(track: TrackEntity?, aligned: List<BilingualLyricLine>): Long {
        val dbDuration = track?.durationMs ?: 0L
        if (dbDuration > 0L) return dbDuration
        val lastTimed = aligned.lastOrNull { it.startTimeMs != null }?.startTimeMs ?: 0L
        return if (lastTimed > 0L) lastTimed + 8000L else 180_000L
    }

    private fun findActiveLineIndex(lyrics: List<BilingualLyricLine>, posMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var lastIdx = -1
        for (i in lyrics.indices) {
            val t = lyrics[i].startTimeMs
            if (t != null && t <= posMs) {
                lastIdx = i
            }
        }
        return lastIdx
    }

    fun selectTrack(index: Int, notifyCdPlayer: Boolean = true) {
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        if (index in tracks.indices) {
            val previousTrackIndex = _uiState.value.currentTrackIndex
            val delta = kotlin.math.abs(index - previousTrackIndex)
            if (notifyCdPlayer) {
                userWantsPlay = true
                userTrackSelectionLockoutMs = (3500L + delta * 600L).coerceAtMost(10000L)
                userTrackSelectionTimestamp = System.currentTimeMillis()
                userSelectedTrackIndex = index
            }
            val track = tracks[index]
            companionAnchorTime = android.os.SystemClock.elapsedRealtime()
            companionAnchorPositionMs = 0L

            val cleanLyrics = if (LyricSanitizer.hasCensorship(track.originalLyrics)) {
                LyricSanitizer.decensorLyrics(track.originalLyrics ?: "")
            } else (track.originalLyrics ?: "")
            val cleanChinese = if (LyricSanitizer.hasCensorship(track.translatedLyrics)) {
                LyricSanitizer.decensorChineseLyrics(track.translatedLyrics, cleanLyrics)
            } else track.translatedLyrics

            if (cleanLyrics != track.originalLyrics || cleanChinese != track.translatedLyrics) {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateTrackTranslation(track.id, track.translatedTitle, cleanLyrics, cleanChinese)
                }
            }

            val aligned = LyricAligner.align(cleanLyrics, cleanChinese)
            val duration = computeTrackDuration(track, aligned)
            currentTrackRawAnnotations = emptyList()
            lastLoadedAnnotationTrackId = null
            _uiState.update {
                it.copy(
                    currentTrackIndex = index,
                    alignedLyrics = aligned,
                    lyricOffsetMs = 0L,
                    currentPositionMs = 0L,
                    trackDurationMs = duration,
                    activeLineIndex = -1,
                    songStory = null,
                    lineAnnotations = emptyMap(),
                    isCdTracklistOpen = false,
                    expandedAnnotationLineIndex = null,
                    annotationLoadState = com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADING
                )
            }
            if (isNotificationActive) {
                updateMediaNotification()
            }
            trackSelectionJob?.cancel()
            startObservingTrackAnnotations(track.id, index)
            trackSelectionJob = viewModelScope.launch {
                val cachedStory = annotationRepository.getSongStory(track.id)
                val cachedAnnotations = annotationRepository.getAnnotations(track.id)
                if ((cachedStory != null || cachedAnnotations.isNotEmpty()) && _uiState.value.currentTrackIndex == index) {
                    currentTrackRawAnnotations = cachedAnnotations
                    val refLines = cachedAnnotations.map { it.lyricFragment }
                    val currentAligned = _uiState.value.alignedLyrics
                    val hasAsterisks = currentAligned.any { it.original.contains('*') || (it.translation?.contains('*') == true) }
                    val finalAligned = if (hasAsterisks && refLines.isNotEmpty()) {
                        val reOriginal = LyricSanitizer.decensorLyrics(track.originalLyrics ?: "", refLines)
                        val reZh = LyricSanitizer.decensorChineseLyrics(track.translatedLyrics, reOriginal)
                        repository.updateTrackTranslation(track.id, track.translatedTitle, reOriginal, reZh)
                        LyricAligner.align(reOriginal, reZh)
                    } else currentAligned

                    val lineMap = LyricFragmentMatcher.matchAnnotationsToLines(finalAligned, cachedAnnotations)
                    _uiState.update {
                        it.copy(
                            alignedLyrics = finalAligned,
                            songStory = cachedStory,
                            lineAnnotations = lineMap,
                            annotationLoadState = com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADED
                        )
                    }
                    if (isNotificationActive) {
                        updateMediaNotification()
                    }
                }

                val offset = lyricOffsetDao.getOffset(track.id)?.offsetMs ?: 0L
                if (offset != 0L && _uiState.value.currentTrackIndex == index) {
                    val baseAligned = _uiState.value.alignedLyrics
                    val withOffset = baseAligned.map { line ->
                        if (line.startTimeMs != null) {
                            line.copy(startTimeMs = (line.startTimeMs + offset).coerceAtLeast(0L))
                        } else line
                    }
                    val durationWithOffset = computeTrackDuration(track, withOffset)
                    val rematched = rematchAnnotations(withOffset)
                    _uiState.update {
                        it.copy(
                            alignedLyrics = withOffset,
                            lyricOffsetMs = offset,
                            trackDurationMs = durationWithOffset,
                            lineAnnotations = if (rematched.isNotEmpty()) rematched else it.lineAnnotations
                        )
                    }
                    if (isNotificationActive) {
                        updateMediaNotification()
                    }
                }
            }
            loadAnnotationsForCurrentTrack()

            // 动态切歌时按近邻优先级静默预热后续曲目的典故与翻译
            val album = _uiState.value.albumWithTracks?.album
            if (album != null && tracks.size > 1) {
                annotationRepository.enqueueAlbumPrefetch(
                    albumId = album.id,
                    artist = album.artist,
                    tracks = tracks,
                    currentTrackIndex = index
                )
            }
            if (notifyCdPlayer && _uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                // CdPlayQueueReq.index is 0-based (0 for 1st song, 1 for 2nd song...)
                val cdQueueIndex = if (track.trackNumber > 0) track.trackNumber - 1 else index
                val prevCdQueueIndex = if (previousTrackIndex in tracks.indices) {
                    val prevTrack = tracks[previousTrackIndex]
                    if (prevTrack.trackNumber > 0) prevTrack.trackNumber - 1 else previousTrackIndex
                } else {
                    previousTrackIndex
                }
                shanlingBluetoothManager.playTrack(cdQueueIndex, prevCdQueueIndex)
            }
        }
    }

    private var companionJob: kotlinx.coroutines.Job? = null

    fun toggleCompanionPlay() {
        val willPlay = !_uiState.value.isCompanionPlaying
        userWantsPlay = willPlay
        if (willPlay) {
            startCompanionInternal()
            if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                shanlingBluetoothManager.play()
            }
        } else {
            pauseCompanionInternal()
            if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
                shanlingBluetoothManager.pause()
            }
        }
    }

    fun startCompanion() {
        userWantsPlay = true
        startCompanionInternal()
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.play()
        }
    }

    private fun updateMediaNotification(forcePlayingState: Boolean? = null) {
        if (!isNotificationActive && forcePlayingState != true) return
        val state = _uiState.value
        val tracks = state.albumWithTracks?.tracks ?: emptyList()
        val track = tracks.getOrNull(state.currentTrackIndex)
        val album = state.albumWithTracks?.album
        val isPlaying = forcePlayingState ?: state.isCompanionPlaying

        val activeLine = state.alignedLyrics.getOrNull(state.activeLineIndex)
        val activeAnnot = state.lineAnnotations[state.activeLineIndex]

        val lyricSnippet = activeLine?.let {
            if (!it.translation.isNullOrBlank()) "${it.original} • ${it.translation}" else it.original
        }
        val annotSnippet = activeAnnot?.let {
            (it.explanationTranslation ?: it.explanationText).replace("\n", " ").trim().take(60)
        }

        val trackTitle = track?.title ?: if (state.cdCurrentTrackNumber > 0) "Track ${state.cdCurrentTrackNumber}" else "Track ${state.currentTrackIndex + 1}"
        val artist = album?.artist ?: state.cdDeviceName ?: "LinerNotes"
        val albumTitle = album?.title ?: if (state.cdDeviceName != null) "CD 播放中" else ""

        val data = MediaPlaybackNotificationData(
            albumId = album?.id ?: "",
            trackTitle = trackTitle,
            artist = artist,
            albumTitle = albumTitle,
            coverUrl = album?.coverUrl,
            isPlaying = isPlaying,
            positionMs = state.currentPositionMs,
            durationMs = state.trackDurationMs,
            activeLyricSnippet = lyricSnippet,
            activeAnnotationSnippet = annotSnippet,
            hasPrevious = state.currentTrackIndex > 0,
            hasNext = state.currentTrackIndex < (tracks.size - 1).coerceAtLeast(0)
        )
        CompanionPlaybackService.update(context, data)
    }

    private fun startCompanionInternal() {
        companionJob?.cancel()
        isNotificationActive = true
        _uiState.update { it.copy(isCompanionPlaying = true) }
        companionAnchorTime = android.os.SystemClock.elapsedRealtime()
        companionAnchorPositionMs = _uiState.value.currentPositionMs
        updateMediaNotification(forcePlayingState = true)
        companionJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100L)
                val currentState = _uiState.value
                if (!currentState.isCompanionPlaying) break

                val elapsed = android.os.SystemClock.elapsedRealtime() - companionAnchorTime
                val currentPos = companionAnchorPositionMs + elapsed
                val duration = currentState.trackDurationMs.coerceAtLeast(10_000L)

                if (currentPos >= duration) {
                    val tracks = currentState.albumWithTracks?.tracks ?: emptyList()
                    val nextIndex = currentState.currentTrackIndex + 1
                    if (nextIndex in tracks.indices) {
                        selectTrack(nextIndex, notifyCdPlayer = false)
                        continue
                    } else {
                        _uiState.update {
                            it.copy(
                                isCompanionPlaying = false,
                                currentPositionMs = 0L,
                                activeLineIndex = -1
                            )
                        }
                        if (isNotificationActive) {
                            updateMediaNotification(forcePlayingState = false)
                        }
                        break
                    }
                } else {
                    val activeIdx = findActiveLineIndex(currentState.alignedLyrics, currentPos)
                    val lineChanged = activeIdx != currentState.activeLineIndex
                    _uiState.update {
                        it.copy(
                            currentPositionMs = currentPos,
                            activeLineIndex = activeIdx
                        )
                    }
                    if (lineChanged && isNotificationActive) {
                        updateMediaNotification()
                    }
                }
            }
        }
    }

    fun pauseCompanion() {
        userWantsPlay = false
        pauseCompanionInternal()
        if (_uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.pause()
        }
    }

    private fun pauseCompanionInternal() {
        companionJob?.cancel()
        companionJob = null
        _uiState.update { it.copy(isCompanionPlaying = false) }
        if (isNotificationActive) {
            updateMediaNotification(forcePlayingState = false)
        }
    }

    fun seekCompanion(targetMs: Long, notifyCdPlayer: Boolean = true) {
        if (notifyCdPlayer) {
            userSeekTimestamp = System.currentTimeMillis()
        }
        val duration = _uiState.value.trackDurationMs.coerceAtLeast(1000L)
        val clamped = targetMs.coerceIn(0L, duration)
        companionAnchorTime = android.os.SystemClock.elapsedRealtime()
        companionAnchorPositionMs = clamped
        val activeIdx = findActiveLineIndex(_uiState.value.alignedLyrics, clamped)
        _uiState.update {
            it.copy(
                currentPositionMs = clamped,
                activeLineIndex = activeIdx
            )
        }
        if (isNotificationActive) {
            updateMediaNotification()
        }
        if (notifyCdPlayer && _uiState.value.cdConnectionState == CdConnectionState.CONNECTED) {
            shanlingBluetoothManager.seekTo((clamped / 1000).toInt())
        }
    }

    fun adjustCompanionOffset(deltaMs: Long) {
        val currentTrack = getCurrentTrack()
        val newOffset = _uiState.value.lyricOffsetMs + deltaMs
        val currentAligned = _uiState.value.alignedLyrics
        val reAligned = currentAligned.map { line ->
            if (line.startTimeMs != null) {
                line.copy(startTimeMs = (line.startTimeMs + deltaMs).coerceAtLeast(0L))
            } else line
        }
        val activeIdx = findActiveLineIndex(reAligned, _uiState.value.currentPositionMs)
        _uiState.update {
            it.copy(
                lyricOffsetMs = newOffset,
                alignedLyrics = reAligned,
                activeLineIndex = activeIdx,
                userMessage = "时间轴已校准: ${if (newOffset >= 0) "+$newOffset" else "$newOffset"} ms"
            )
        }
        if (currentTrack != null) {
            viewModelScope.launch {
                lyricOffsetDao.saveOffset(LyricOffsetEntity(trackId = currentTrack.id, offsetMs = newOffset))
            }
        }
    }

    fun resetCompanionOffset() {
        val currentTrack = getCurrentTrack()
        val currentOffset = _uiState.value.lyricOffsetMs
        if (currentOffset == 0L) return
        val deltaMs = -currentOffset
        val currentAligned = _uiState.value.alignedLyrics
        val reAligned = currentAligned.map { line ->
            if (line.startTimeMs != null) {
                line.copy(startTimeMs = (line.startTimeMs + deltaMs).coerceAtLeast(0L))
            } else line
        }
        val activeIdx = findActiveLineIndex(reAligned, _uiState.value.currentPositionMs)
        _uiState.update {
            it.copy(
                lyricOffsetMs = 0L,
                alignedLyrics = reAligned,
                activeLineIndex = activeIdx,
                userMessage = "时间轴校准已重置为 0ms"
            )
        }
        if (currentTrack != null) {
            viewModelScope.launch {
                lyricOffsetDao.deleteOffset(currentTrack.id)
            }
        }
    }

    fun setFuriganaMode(mode: FuriganaMode) {
        _uiState.update { it.copy(furiganaMode = mode) }
    }

    fun cycleFuriganaMode() {
        val nextMode = when (_uiState.value.furiganaMode) {
            FuriganaMode.OFF -> FuriganaMode.HIRAGANA
            FuriganaMode.HIRAGANA -> FuriganaMode.ROMAJI
            FuriganaMode.ROMAJI -> FuriganaMode.OFF
        }
        _uiState.update {
            it.copy(
                furiganaMode = nextMode,
                userMessage = when (nextMode) {
                    FuriganaMode.OFF -> "已关闭日语注音"
                    FuriganaMode.HIRAGANA -> "已开启日文假名注音 (Furigana)"
                    FuriganaMode.ROMAJI -> "已开启日文罗马音 (Romaji)"
                }
            )
        }
    }

    fun toggleDiscViewExpanded() {
        _uiState.update { it.copy(isDiscViewExpanded = !it.isDiscViewExpanded) }
    }

    fun getExportableLrc(): String {
        val state = _uiState.value
        val track = getCurrentTrack()
        val album = state.albumWithTracks?.album
        return LrcExporter.generateLrc(
            lines = state.alignedLyrics,
            offsetMs = 0L, // Already applied in alignedLyrics
            title = track?.title,
            artist = album?.artist,
            album = album?.title
        )
    }

    fun forcePrewarmAlbum() {
        val album = _uiState.value.albumWithTracks?.album ?: return
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        annotationRepository.enqueueAlbumPrefetch(
            albumId = album.id,
            artist = album.artist,
            tracks = tracks,
            currentTrackIndex = _uiState.value.currentTrackIndex,
            forceRefresh = true
        )
        _uiState.update { it.copy(userMessage = "已启动全专辑典故与翻译后台静默预热") }
    }

    fun loadAnnotationsForCurrentTrack(forceRefresh: Boolean = false) {
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        val index = _uiState.value.currentTrackIndex
        val track = tracks.getOrNull(index) ?: return
        val artist = _uiState.value.albumWithTracks?.album?.artist ?: ""

        if (!forceRefresh && track.id == lastLoadedAnnotationTrackId && _uiState.value.lineAnnotations.isNotEmpty()) {
            return
        }

        val lyricTexts = _uiState.value.alignedLyrics.map { it.original }.ifEmpty {
            track.originalLyrics?.lines()
                ?.map { it.replace(Regex("""^\[\d+:\d+(?:\.\d+)?\]"""), "").trim() }
                ?.filter { it.isNotBlank() && !it.startsWith("[") } ?: emptyList()
        }

        val isTrad = _uiState.value.isTraditionalMode ||
            aiPreferences.targetLanguage == "zh-TW" ||
            com.linernotes.app.core.i18n.TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_TW ||
            ChineseConverter.isTraditional(track.translatedLyrics) ||
            ChineseConverter.isTraditional(track.translatedTitle)

        loadAnnotationsJob?.cancel()
        startObservingTrackAnnotations(track.id, index)
        loadAnnotationsJob = viewModelScope.launch {
            val hasExisting = _uiState.value.songStory != null || _uiState.value.lineAnnotations.isNotEmpty()
            _uiState.update { 
                it.copy(
                    isLoadingAnnotations = true, 
                    isTraditionalMode = isTrad,
                    annotationLoadState = if (hasExisting) com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADED else com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADING
                ) 
            }
            val result = annotationRepository.fetchAndCacheAnnotations(track, artist, lyricTexts, forceRefresh)
            if (_uiState.value.currentTrackIndex != index) return@launch
            result.onSuccess { (story, annotations) ->
                lastLoadedAnnotationTrackId = track.id
                currentTrackRawAnnotations = annotations
                val currentAligned = _uiState.value.alignedLyrics
                val refFragments = annotations.map { it.lyricFragment }
                val curTrack = getCurrentTrack()
                val hasAsterisks = currentAligned.any { it.original.contains('*') || (it.translation?.contains('*') == true) }

                val finalAligned = if (hasAsterisks && curTrack != null && refFragments.isNotEmpty()) {
                    val decensoredOrig = LyricSanitizer.decensorLyrics(curTrack.originalLyrics ?: "", refFragments)
                    val decensoredZh = LyricSanitizer.decensorChineseLyrics(curTrack.translatedLyrics, decensoredOrig)
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.updateTrackTranslation(curTrack.id, curTrack.translatedTitle, decensoredOrig, decensoredZh)
                    }
                    LyricAligner.align(decensoredOrig, decensoredZh)
                } else currentAligned

                val lineMap = LyricFragmentMatcher.matchAnnotationsToLines(
                    finalAligned,
                    annotations
                )
                val hasContent = (story != null || lineMap.isNotEmpty())
                _uiState.update {
                    it.copy(
                        alignedLyrics = finalAligned,
                        songStory = story ?: it.songStory,
                        lineAnnotations = lineMap,
                        isLoadingAnnotations = false,
                        isTraditionalMode = isTrad,
                        annotationLoadState = if (hasContent || lineMap.isNotEmpty()) {
                            com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADED
                        } else {
                            com.linernotes.app.presentation.booklet.model.AnnotationLoadState.EMPTY
                        }
                    )
                }
                if (isNotificationActive) {
                    updateMediaNotification()
                }
            }.onFailure {
                lastLoadedAnnotationTrackId = null
                val hasContent = (_uiState.value.songStory != null || _uiState.value.lineAnnotations.isNotEmpty())
                _uiState.update { 
                    it.copy(
                        isLoadingAnnotations = false,
                        annotationLoadState = if (hasContent) com.linernotes.app.presentation.booklet.model.AnnotationLoadState.LOADED else com.linernotes.app.presentation.booklet.model.AnnotationLoadState.FAILED
                    ) 
                }
            }
        }
    }

    fun openAnnotation(annotation: LyricAnnotationEntity) {
        _uiState.update {
            it.copy(
                selectedAnnotation = annotation,
                isAnnotationSheetOpen = true
            )
        }
        if (annotation.explanationTranslation.isNullOrBlank() && !AiAnnotationCurator.isAlreadyChinese(annotation.explanationText) && !_uiState.value.isTranslatingAnnotation) {
            translateAnnotation(annotation)
        }
    }

    fun dismissAnnotationSheet() {
        _uiState.update {
            it.copy(
                isAnnotationSheetOpen = false,
                selectedAnnotation = null
            )
        }
    }

    fun toggleSongStoryExpanded() {
        val willExpand = !_uiState.value.isSongStoryExpanded
        _uiState.update { it.copy(isSongStoryExpanded = !it.isSongStoryExpanded) }
        if (willExpand) {
            val story = _uiState.value.songStory
            if (story != null && story.descriptionTranslation.isNullOrBlank() && !AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain) && !_uiState.value.isTranslatingSongStory) {
                translateSongStory(story)
            }
        }
    }

    fun translateAnnotation(annotation: LyricAnnotationEntity, lineIndex: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslatingAnnotation = true) }
            val updated = annotationRepository.translateAnnotation(annotation)
            _uiState.update { state ->
                val updatedMap = state.lineAnnotations.mapValues { (idx, v) ->
                    val matches = if (lineIndex != null && idx == lineIndex) {
                        true
                    } else if (updated.id > 0L && v.id > 0L) {
                        v.id == updated.id
                    } else {
                        v.lyricFragment == updated.lyricFragment && v.explanationText == updated.explanationText
                    }
                    if (matches) updated else v
                }
                val isSelectedMatch = state.selectedAnnotation?.let { sel ->
                    (updated.id > 0L && sel.id > 0L && sel.id == updated.id) ||
                    (sel.lyricFragment == updated.lyricFragment && sel.explanationText == updated.explanationText)
                } ?: false
                state.copy(
                    isTranslatingAnnotation = false,
                    selectedAnnotation = if (isSelectedMatch) updated else state.selectedAnnotation,
                    lineAnnotations = updatedMap
                )
            }
            if (isNotificationActive) {
                updateMediaNotification()
            }
        }
    }

    fun translateSongStory(story: SongStoryEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslatingSongStory = true) }
            val updated = annotationRepository.translateSongStory(story)
            _uiState.update {
                it.copy(
                    isTranslatingSongStory = false,
                    songStory = updated
                )
            }
        }
    }

    fun onLyricLineClicked(lineIndex: Int, line: BilingualLyricLine) {
        if (line.startTimeMs != null) {
            seekCompanion(line.startTimeMs)
        }
    }

    fun onLyricLineClicked(line: BilingualLyricLine) {
        val lineIndex = _uiState.value.alignedLyrics.indexOfFirst { it.lineNumber == line.lineNumber }
        onLyricLineClicked(if (lineIndex >= 0) lineIndex else 0, line)
    }

    fun toggleInlineAnnotation(lineIndex: Int) {
        val willExpand = _uiState.value.expandedAnnotationLineIndex != lineIndex
        _uiState.update { current ->
            val newIndex = if (current.expandedAnnotationLineIndex == lineIndex) null else lineIndex
            current.copy(expandedAnnotationLineIndex = newIndex)
        }
        if (willExpand) {
            val annot = _uiState.value.lineAnnotations[lineIndex]
            if (annot != null && annot.explanationTranslation.isNullOrBlank() && !AiAnnotationCurator.isAlreadyChinese(annot.explanationText) && !_uiState.value.isTranslatingAnnotation) {
                translateAnnotation(annot, lineIndex)
            }
        }
    }

    fun collapseInlineAnnotation() {
        _uiState.update { it.copy(expandedAnnotationLineIndex = null) }
    }

    fun toggleImmersiveMode() {
        _uiState.update { it.copy(isImmersiveMode = !it.isImmersiveMode) }
    }

    fun setImmersiveMode(enabled: Boolean) {
        _uiState.update { it.copy(isImmersiveMode = enabled) }
    }

    fun toggleCalibrationBar() {
        _uiState.update { it.copy(showCalibrationBar = !it.showCalibrationBar) }
    }

    fun onExternalCdStateReceived(queueIndex: Int, posMs: Long, isPlaying: Boolean) {
        if (queueIndex < 0) return
        val tracks = _uiState.value.albumWithTracks?.tracks
        val targetIndex = if (tracks.isNullOrEmpty()) {
            queueIndex
        } else {
            resolveTrackIndex(tracks, queueIndex)
        }

        val now = System.currentTimeMillis()
        val isWithinLockout = (now - userTrackSelectionTimestamp) < userTrackSelectionLockoutMs

        if (isWithinLockout) {
            if (targetIndex != userSelectedTrackIndex) {
                // CD 机光头物理寻道中（通常需 1.5~2.5 秒），严格忽略旧音轨帧上报，防止 UI 闪跳回退
                return
            }
            // 物理光头已到达目标曲目，若 CD 此时处于暂停状态且用户期望播放，主动唤醒 CD 伺服起播
            if (!isPlaying && userWantsPlay) {
                shanlingBluetoothManager.play()
            }
            // 收到新音轨确认后，仅在距用户操作满 1.5 秒后才解除保护罩，确保物理硬件稳态
            if (now - userTrackSelectionTimestamp > 1500L) {
                userTrackSelectionTimestamp = 0L
            }
        }

        if (targetIndex != -1 && targetIndex != _uiState.value.currentTrackIndex) {
            if (!tracks.isNullOrEmpty() && targetIndex in tracks.indices) {
                selectTrack(targetIndex, notifyCdPlayer = false)
            } else {
                _uiState.update { it.copy(currentTrackIndex = targetIndex) }
                if (isNotificationActive) {
                    updateMediaNotification()
                }
            }
        }
        if (now - userSeekTimestamp >= 1500L && posMs > 0L) {
            seekCompanion(posMs, notifyCdPlayer = false)
        }
        if (isPlaying && !_uiState.value.isCompanionPlaying) {
            startCompanionInternal()
        } else if (!isPlaying && _uiState.value.isCompanionPlaying) {
            if (userWantsPlay) {
                // 用户期望播放但 CD 处于暂停（如机械跳轨到位瞬间），主动指令 CD 机起播并保持伴奏播放
                shanlingBluetoothManager.play()
            } else {
                pauseCompanionInternal()
            }
        }
    }

    private fun resolveTrackIndex(tracks: List<TrackEntity>, cdQueueIndex: Int): Int {
        if (tracks.isEmpty()) return -1
        val physicalCdTrackNo = cdQueueIndex + 1

        // 1. 0-based 物理队列位置与已排好序的曲目直接对应（首选标准 Red Book 1:1 映射）
        if (cdQueueIndex in tracks.indices && tracks[cdQueueIndex].trackNumber == physicalCdTrackNo) {
            return cdQueueIndex
        }

        // 2. 匹配 TrackEntity.trackNumber 与 1-based 物理音轨号（兼容曲目乱序或不连续情况）
        val exactTrackNumberMatch = tracks.indexOfFirst { it.trackNumber == physicalCdTrackNo }
        if (exactTrackNumberMatch != -1) return exactTrackNumberMatch

        // 3. 容错回退：按 0-based 列表位置直接定位
        if (cdQueueIndex in tracks.indices) return cdQueueIndex

        return 0
    }

    fun openCdTracklist(isOpen: Boolean) {
        _uiState.update { it.copy(isCdTracklistOpen = isOpen) }
    }

    fun openCdMatchAlbum(isOpen: Boolean) {
        _uiState.update { it.copy(isCdMatchAlbumOpen = isOpen) }
    }

    fun playCdTrack(trackIndex: Int) {
        userWantsPlay = true
        val tracks = _uiState.value.albumWithTracks?.tracks ?: emptyList()
        if (trackIndex in tracks.indices) {
            selectTrack(trackIndex, notifyCdPlayer = true)
        } else {
            // 未匹配唱片曲目时，直接指令 CD 机播放对应的 0-based 物理音轨索引
            val previousTrackIndex = _uiState.value.currentTrackIndex
            val delta = kotlin.math.abs(trackIndex - previousTrackIndex)
            userTrackSelectionLockoutMs = (3500L + delta * 600L).coerceAtMost(10000L)
            userTrackSelectionTimestamp = System.currentTimeMillis()
            userSelectedTrackIndex = trackIndex
            _uiState.update { it.copy(currentTrackIndex = trackIndex, isCdTracklistOpen = false) }
            shanlingBluetoothManager.playTrack(trackIndex, previousTrackIndex)
        }
        _uiState.update { it.copy(isCdTracklistOpen = false) }
        startCompanionInternal()
    }

    fun switchAlbum(albumId: String) {
        if (currentAlbumId != albumId) {
            currentAlbumId = albumId
            loadBooklet(albumId)
            _uiState.update { it.copy(isCdTracklistOpen = false, isCdMatchAlbumOpen = false) }
        }
    }

    fun saveAndBindMatchedAlbum(album: com.linernotes.app.data.local.entity.AlbumEntity, tracks: List<TrackEntity>) {
        viewModelScope.launch {
            val savedTracks = repository.saveAlbum(album, tracks)
            switchAlbum(album.id)
            _uiState.update {
                it.copy(
                    isCdMatchAlbumOpen = false,
                    isCdTracklistOpen = false,
                    userMessage = "已成功为当前 CD 匹配唱片《${album.title}》"
                )
            }
            batchFetchOfficialLyricsAlbum()
            annotationRepository.enqueueAlbumPrefetch(album.id, album.artist, savedTracks)
            batchTranslationManager.autoFillMissingTranslations(album.id, album.title, savedTracks)
        }
    }

    fun openCdSheet(isOpen: Boolean) {
        _uiState.update { it.copy(isCdSheetOpen = isOpen) }
    }

    fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return shanlingBluetoothManager.getAllPairedDevices()
    }

    fun connectCdPlayer(device: BluetoothDevice? = null) {
        shanlingBluetoothManager.connectToDevice(device)
    }

    fun disconnectCdPlayer() {
        shanlingBluetoothManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        isNotificationActive = false
        mediaNotificationManager.commandHandler = null
        mediaNotificationManager.clear()
        CompanionPlaybackService.stop(context)
        companionJob?.cancel()
        shanlingBluetoothManager.disconnect()
    }

    fun previousTrack() {
        val target = _uiState.value.currentTrackIndex - 1
        if (target >= 0) {
            playCdTrack(target)
        } else {
            seekCompanion(0L)
        }
    }

    fun nextTrack() {
        val tracks = _uiState.value.albumWithTracks?.tracks
        val totalCount = if (!tracks.isNullOrEmpty()) tracks.size else _uiState.value.cdTotalTracks
        val target = _uiState.value.currentTrackIndex + 1
        if (totalCount > 0 && target < totalCount) {
            playCdTrack(target)
        }
    }

    fun updateAmbientColor(color: Color) {
        _uiState.update { it.copy(ambientCoverColor = color) }
    }

    fun openEditSheet(isOpen: Boolean) {
        _uiState.update { it.copy(isEditingSheetOpen = isOpen) }
    }

    fun openSettings(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun saveManualEdits(trackId: Long, newTitleZh: String?, newOriginal: String?, newTranslated: String?) {
        viewModelScope.launch {
            repository.updateTrackTranslation(
                trackId = trackId,
                translatedTitle = newTitleZh,
                originalLyrics = newOriginal,
                translatedLyrics = newTranslated
            )
            _uiState.update { it.copy(isEditingSheetOpen = false, userMessage = "校对已成功保存至本地") }
        }
    }

    fun setTranslateMenuOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isTranslateMenuOpen = isOpen) }
    }

    fun onAiTranslateClicked() {
        _uiState.update { it.copy(isTranslateMenuOpen = true) }
    }

    fun fetchOfficialLyricsCurrentTrack() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val currentTrack = getCurrentTrack() ?: return
        val artist = _uiState.value.albumWithTracks?.album?.artist ?: ""

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, userMessage = "正在检索多源官方歌词...") }
            try {
                val result = com.linernotes.app.data.remote.UnifiedLyricsService.fetchLyrics(
                    trackTitle = currentTrack.title,
                    artistName = artist,
                    sourcePref = aiPreferences.lyricsSource
                )
                if (result != null && result.originalLyrics.isNotBlank()) {
                    repository.updateTrackTranslation(
                        trackId = currentTrack.id,
                        translatedTitle = currentTrack.translatedTitle,
                        originalLyrics = result.originalLyrics,
                        translatedLyrics = result.translatedLyrics
                    )

                    // 若官方库仅检索到原版（无官方译文），且启用了智能回退，则自动调用翻译服务补全翻译
                    if (!result.isBilingual && aiPreferences.lyricsSource == AiPreferences.LyricsSourcePreference.AUTO_FIRST.code) {
                        _uiState.update { it.copy(userMessage = "已匹配官方原版歌词，正在自动翻译...") }
                        val transResult = translationService.translateTrack(
                            trackTitle = currentTrack.title,
                            originalLyrics = result.originalLyrics
                        )
                        repository.updateTrackTranslation(
                            trackId = currentTrack.id,
                            translatedTitle = transResult.translatedTitle ?: currentTrack.translatedTitle,
                            originalLyrics = result.originalLyrics,
                            translatedLyrics = transResult.translatedLyrics
                        )
                        _uiState.update { it.copy(isTranslating = false, userMessage = "官方原版歌词已入库，翻译已同步补全！") }
                    } else {
                        val msg = if (result.isBilingual) "官方双语歌词已匹配并同步入库！" else "已检索到官方原版歌词（暂无官方译文）"
                        _uiState.update { it.copy(isTranslating = false, userMessage = msg) }
                    }
                } else {
                    // 若官方库未搜到且启用了智能回退，则尝试翻译当前已有歌词
                    if (aiPreferences.lyricsSource == AiPreferences.LyricsSourcePreference.AUTO_FIRST.code && !currentTrack.originalLyrics.isNullOrBlank()) {
                        _uiState.update { it.copy(userMessage = "在线歌词库未检索到，正在自动回退机器翻译...") }
                        val transResult = translationService.translateTrack(
                            trackTitle = currentTrack.title,
                            originalLyrics = currentTrack.originalLyrics
                        )
                        repository.updateTrackTranslation(
                            trackId = currentTrack.id,
                            translatedTitle = transResult.translatedTitle ?: currentTrack.translatedTitle,
                            originalLyrics = currentTrack.originalLyrics,
                            translatedLyrics = transResult.translatedLyrics
                        )
                        _uiState.update { it.copy(isTranslating = false, userMessage = "翻译完成并已保存！") }
                    } else {
                        _uiState.update { it.copy(isTranslating = false, userMessage = "未检索到该歌曲歌词，可尝试切换歌词源或手动添加") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, userMessage = "匹配歌词遇到问题: ${e.message}") }
            }
        }
    }

    fun batchFetchOfficialLyricsAlbum() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val tracks = _uiState.value.albumWithTracks?.tracks ?: return
        val artist = _uiState.value.albumWithTracks?.album?.artist ?: ""
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, userMessage = "正在极速多源检索全辑官方歌词 (共 ${tracks.size} 首)...") }
            var matchedCount = 0
            try {
                for ((index, track) in tracks.withIndex()) {
                    _uiState.update { it.copy(userMessage = "正在检索 [${index + 1}/${tracks.size}] ${track.title}...") }
                    val result = com.linernotes.app.data.remote.UnifiedLyricsService.fetchLyrics(
                        trackTitle = track.title,
                        artistName = artist,
                        sourcePref = aiPreferences.lyricsSource
                    )
                    if (result != null && result.originalLyrics.isNotBlank()) {
                        repository.updateTrackTranslation(
                            trackId = track.id,
                            translatedTitle = track.translatedTitle,
                            originalLyrics = result.originalLyrics,
                            translatedLyrics = result.translatedLyrics
                        )
                        matchedCount++
                    }
                }
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        userMessage = "全辑官方歌词匹配完成！已成功收录 $matchedCount / ${tracks.size} 首曲目"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, userMessage = "全辑匹配中途出错: ${e.message}") }
            }
        }
    }

    fun startBatchAlbumTranslation() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val album = _uiState.value.albumWithTracks?.album ?: return
        batchTranslationManager.startBatchTranslation(album.id, album.title)
    }

    fun cancelBatchAlbumTranslation() {
        batchTranslationManager.cancelBatchTranslation()
    }

    fun retranslateCurrentTrack() {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val currentTrack = getCurrentTrack() ?: return
        if (currentTrack.originalLyrics.isNullOrBlank()) {
            _uiState.update { it.copy(userMessage = "当前曲目无歌词，无法进行翻译") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, userMessage = "正在逐行翻译歌词中...") }
            try {
                val result = translationService.translateTrack(
                    trackTitle = currentTrack.title,
                    originalLyrics = currentTrack.originalLyrics
                )

                repository.updateTrackTranslation(
                    trackId = currentTrack.id,
                    translatedTitle = result.translatedTitle ?: currentTrack.translatedTitle,
                    originalLyrics = currentTrack.originalLyrics,
                    translatedLyrics = result.translatedLyrics
                )
                _uiState.update { it.copy(isTranslating = false, userMessage = "翻译完成并已对齐保存！") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTranslating = false, userMessage = "翻译遇到问题: ${e.message}") }
            }
        }
    }

    fun convertCurrentTrackTranslation(toTraditional: Boolean) {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val currentTrack = getCurrentTrack() ?: return
        if (currentTrack.translatedLyrics.isNullOrBlank() && currentTrack.translatedTitle.isNullOrBlank()) {
            _uiState.update { it.copy(userMessage = "当前曲目暂无译文可转换") }
            return
        }

        viewModelScope.launch {
            val newTitle = if (toTraditional) {
                ChineseConverter.toTraditional(currentTrack.translatedTitle)
            } else {
                ChineseConverter.toSimplified(currentTrack.translatedTitle)
            }
            val newLyrics = if (toTraditional) {
                ChineseConverter.toTraditional(currentTrack.translatedLyrics)
            } else {
                ChineseConverter.toSimplified(currentTrack.translatedLyrics)
            }

            repository.updateTrackTranslation(
                trackId = currentTrack.id,
                translatedTitle = newTitle.ifBlank { null },
                originalLyrics = currentTrack.originalLyrics,
                translatedLyrics = newLyrics
            )

            // 同步转换当前曲目的 Genius 背景故事
            val currentStory = _uiState.value.songStory
            val newStory = currentStory?.let { s ->
                val updatedStory = s.copy(
                    descriptionTranslation = if (toTraditional) ChineseConverter.toTraditional(s.descriptionTranslation) else ChineseConverter.toSimplified(s.descriptionTranslation),
                    descriptionPlain = if (AiAnnotationCurator.isAlreadyChinese(s.descriptionPlain)) {
                        if (toTraditional) ChineseConverter.toTraditional(s.descriptionPlain) else ChineseConverter.toSimplified(s.descriptionPlain)
                    } else s.descriptionPlain
                )
                annotationRepository.updateSongStory(updatedStory)
                updatedStory
            }

            // 同步转换所有逐句典故与歌词翻译
            val newAnnotations = _uiState.value.lineAnnotations.mapValues { (_, annot) ->
                val newExplTrans = if (toTraditional) ChineseConverter.toTraditional(annot.explanationTranslation) else ChineseConverter.toSimplified(annot.explanationTranslation)
                val newLyricTrans = if (toTraditional) ChineseConverter.toTraditional(annot.lyricTranslation) else ChineseConverter.toSimplified(annot.lyricTranslation)
                val newExplText = if (AiAnnotationCurator.isAlreadyChinese(annot.explanationText)) {
                    if (toTraditional) ChineseConverter.toTraditional(annot.explanationText) else ChineseConverter.toSimplified(annot.explanationText)
                } else annot.explanationText
                val updatedAnnot = annot.copy(
                    explanationTranslation = newExplTrans,
                    lyricTranslation = newLyricTrans,
                    explanationText = newExplText
                )
                if (updatedAnnot.id > 0L) {
                    annotationRepository.updateAnnotation(updatedAnnot)
                }
                updatedAnnot
            }

            val currentSelected = _uiState.value.selectedAnnotation
            val newSelected = currentSelected?.let { annot ->
                newAnnotations.values.find {
                    (annot.id > 0L && it.id == annot.id) ||
                    (it.lyricFragment == annot.lyricFragment && it.explanationText == annot.explanationText)
                } ?: annot.copy(
                    explanationTranslation = if (toTraditional) ChineseConverter.toTraditional(annot.explanationTranslation) else ChineseConverter.toSimplified(annot.explanationTranslation),
                    lyricTranslation = if (toTraditional) ChineseConverter.toTraditional(annot.lyricTranslation) else ChineseConverter.toSimplified(annot.lyricTranslation)
                )
            }

            val aligned = LyricAligner.align(currentTrack.originalLyrics, newLyrics)
            currentTrackRawAnnotations = newAnnotations.values.toList()
            _uiState.update { state ->
                state.copy(
                    alignedLyrics = aligned,
                    songStory = newStory,
                    lineAnnotations = newAnnotations,
                    selectedAnnotation = newSelected,
                    isTraditionalMode = toTraditional,
                    userMessage = if (toTraditional) "当前曲目与典故已成功转换为繁体中文" else "当前曲目与典故已成功转换为简体中文"
                )
            }
        }
    }

    fun convertAlbumTranslation(toTraditional: Boolean) {
        _uiState.update { it.copy(isTranslateMenuOpen = false) }
        val albumWithTracks = _uiState.value.albumWithTracks ?: return
        val tracks = albumWithTracks.tracks
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            var convertedCount = 0
            val album = albumWithTracks.album
            if (!album.translatedTitle.isNullOrBlank()) {
                val newAlbumTitle = if (toTraditional) {
                    ChineseConverter.toTraditional(album.translatedTitle)
                } else {
                    ChineseConverter.toSimplified(album.translatedTitle)
                }
                repository.updateAlbumTranslation(album.id, newAlbumTitle.ifBlank { null })
            }

            for (track in tracks) {
                if (!track.translatedLyrics.isNullOrBlank() || !track.translatedTitle.isNullOrBlank()) {
                    val newTitle = if (toTraditional) {
                        ChineseConverter.toTraditional(track.translatedTitle)
                    } else {
                        ChineseConverter.toSimplified(track.translatedTitle)
                    }
                    val newLyrics = if (toTraditional) {
                        ChineseConverter.toTraditional(track.translatedLyrics)
                    } else {
                        ChineseConverter.toSimplified(track.translatedLyrics)
                    }

                    repository.updateTrackTranslation(
                        trackId = track.id,
                        translatedTitle = newTitle.ifBlank { null },
                        originalLyrics = track.originalLyrics,
                        translatedLyrics = newLyrics
                    )
                    convertedCount++
                }
            }

            val trackIds = tracks.map { it.id }
            annotationRepository.convertAllAnnotationsForTracks(trackIds, toTraditional)

            val currentTrack = getCurrentTrack()
            val newAligned = if (currentTrack != null) {
                val newLyrics = if (toTraditional) {
                    ChineseConverter.toTraditional(currentTrack.translatedLyrics)
                } else {
                    ChineseConverter.toSimplified(currentTrack.translatedLyrics)
                }
                LyricAligner.align(currentTrack.originalLyrics, newLyrics)
            } else {
                _uiState.value.alignedLyrics
            }

            val updatedStory = annotationRepository.getSongStory(currentTrack?.id ?: 0L)
            val updatedAnnots = annotationRepository.getAnnotations(currentTrack?.id ?: 0L)
            currentTrackRawAnnotations = updatedAnnots
            val lineMap = LyricFragmentMatcher.matchAnnotationsToLines(newAligned, updatedAnnots)

            val targetType = if (toTraditional) "繁体中文" else "简体中文"
            _uiState.update {
                it.copy(
                    alignedLyrics = newAligned,
                    songStory = updatedStory,
                    lineAnnotations = lineMap,
                    isTraditionalMode = toTraditional,
                    userMessage = "全辑共 $convertedCount 首曲目及典故已成功转换为$targetType"
                )
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun getCurrentTrack(): TrackEntity? {
        val state = _uiState.value
        return state.albumWithTracks?.tracks?.getOrNull(state.currentTrackIndex)
    }

    // ==========================================
    // 实体 CD 版本库 (Discogs) 交互逻辑
    // ==========================================

    fun openDiscogsPicker(isOpen: Boolean) {
        _uiState.update { it.copy(isDiscogsPickerOpen = isOpen) }
        if (isOpen && _uiState.value.discogsResults.isEmpty()) {
            searchDiscogs()
        }
    }

    fun searchDiscogs(query: String? = null) {
        val album = _uiState.value.albumWithTracks?.album
        val rawQuery = query?.trim()
        val token = aiPreferences.discogsToken.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(isDiscogsLoading = true) }
            try {
                val results = if (!rawQuery.isNullOrBlank()) {
                    val cleanBarcode = rawQuery.replace("[^0-9]".toRegex(), "")
                    if (cleanBarcode.length in 8..14 && rawQuery.matches(Regex("^[0-9\\-\\s]+$"))) {
                        val barcodeRes = DiscogsService.searchByBarcode(cleanBarcode, token)
                        if (barcodeRes.isNotEmpty()) barcodeRes
                        else DiscogsService.searchReleases(rawQuery, token)
                    } else {
                        DiscogsService.searchReleases(rawQuery, token)
                    }
                } else if (album != null) {
                    if (!album.barcode.isNullOrBlank()) {
                        val barcodeRes = DiscogsService.searchByBarcode(album.barcode, token)
                        if (barcodeRes.isNotEmpty()) barcodeRes
                        else DiscogsService.searchReleasesByAlbumAndArtist(album.title, album.artist, token)
                    } else {
                        DiscogsService.searchReleasesByAlbumAndArtist(album.title, album.artist, token)
                    }
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        isDiscogsLoading = false,
                        discogsResults = results,
                        userMessage = if (results.isEmpty() && !rawQuery.isNullOrBlank()) "未在 Discogs 找到匹配版本" else it.userMessage
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDiscogsLoading = false,
                        userMessage = "Discogs 检索失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun applyDiscogsRelease(releaseId: Long) {
        val albumWithTracks = _uiState.value.albumWithTracks ?: return
        val album = albumWithTracks.album
        val existingTracks = albumWithTracks.tracks
        val token = aiPreferences.discogsToken.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(isDiscogsLoading = true) }
            val detail = try {
                DiscogsService.fetchReleaseDetail(releaseId, token)
            } catch (e: Exception) {
                null
            }

            if (detail == null) {
                _uiState.update {
                    it.copy(
                        isDiscogsLoading = false,
                        userMessage = "获取 Discogs 版本详情失败，请检查网络或配置 Token"
                    )
                }
                return@launch
            }

            val updatedQuality = when {
                detail.mediaType.contains("SACD", ignoreCase = true) -> "SACD"
                detail.mediaType.contains("XRCD", ignoreCase = true) -> "XRCD"
                detail.mediaType.contains("SHM", ignoreCase = true) -> "SHM-CD"
                detail.mediaType.contains("BSCD", ignoreCase = true) -> "BSCD2"
                detail.mediaType.contains("HDCD", ignoreCase = true) -> "HDCD"
                else -> album.audioQuality ?: "STANDARD"
            }

            val newNotes = buildString {
                val existing = album.notes?.trim()
                if (!existing.isNullOrBlank()) {
                    append(existing)
                    append("\n\n")
                }
                append("【实体 CD 压盘版本】\n")
                append("• 介质规格: ${detail.mediaType}\n")
                if (!detail.country.isNullOrBlank()) append("• 发行国家/地区: ${detail.country}\n")
                if (!detail.year.isNullOrBlank()) append("• 发行年份: ${detail.year}\n")
                if (!detail.label.isNullOrBlank()) append("• 唱片厂牌/编号: ${detail.label}\n")
                if (!detail.barcode.isNullOrBlank()) append("• 条形码: ${detail.barcode}\n")
                if (detail.credits.isNotEmpty()) {
                    append("\n【演职制作名单】\n")
                    detail.credits.take(20).forEach { c ->
                        append("• ${c.role}: ${c.name}\n")
                    }
                }
                if (!detail.notes.isNullOrBlank()) {
                    append("\n【版本档案备注】\n")
                    append(detail.notes)
                }
            }.trim()

            val updatedAlbum = album.copy(
                mediaType = detail.mediaType,
                label = detail.label ?: album.label,
                barcode = detail.barcode ?: album.barcode,
                releaseYear = detail.year ?: album.releaseYear,
                coverUrl = detail.coverUrl ?: album.coverUrl,
                audioQuality = updatedQuality,
                notes = newNotes
            )

            // 对齐音轨列表与持续时间
            val updatedTracks = if (detail.tracklist.isNotEmpty()) {
                detail.tracklist.mapIndexed { idx, dTrack ->
                    val match = existingTracks.find { it.trackNumber == dTrack.trackNumber }
                        ?: existingTracks.find { it.title.equals(dTrack.title, ignoreCase = true) }
                        ?: existingTracks.getOrNull(idx)

                    TrackEntity(
                        id = match?.id ?: 0L,
                        albumId = album.id,
                        trackNumber = dTrack.trackNumber,
                        title = if (match != null && match.title.isNotBlank()) match.title else dTrack.title,
                        translatedTitle = match?.translatedTitle,
                        originalLyrics = match?.originalLyrics,
                        translatedLyrics = match?.translatedLyrics,
                        durationMs = dTrack.durationMs ?: match?.durationMs
                    )
                }
            } else {
                existingTracks
            }

            repository.saveAlbum(updatedAlbum, updatedTracks)

            // 导入内页扫描切片（若此前尚未导入任何内页画册）
            if (detail.bookletImageUrls.isNotEmpty()) {
                val existingPageCount = bookletDao.getPageCount(album.id)
                if (existingPageCount == 0) {
                    val bookletEntities = detail.bookletImageUrls.take(12).mapIndexed { pIdx, imgUrl ->
                        BookletPageEntity(
                            albumId = album.id,
                            pageNumber = pIdx + 1,
                            imagePath = imgUrl,
                            pageType = if (pIdx == 0) "COVER_FRONT" else "CONTENT"
                        )
                    }
                    bookletDao.insertPages(bookletEntities)
                }
            }

            _uiState.update {
                it.copy(
                    isDiscogsLoading = false,
                    isDiscogsPickerOpen = false,
                    selectedDiscogsDetail = null,
                    isDiscogsDetailLoading = false,
                    userMessage = "已成功切换至 [${detail.mediaType} · ${detail.country ?: "实体 CD"}] 压盘版本！"
                )
            }
        }
    }

    fun viewDiscogsReleaseDetail(releaseId: Long) {
        val token = aiPreferences.discogsToken.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscogsDetailLoading = true) }
            val detail = try {
                DiscogsService.fetchReleaseDetail(releaseId, token)
            } catch (e: Exception) {
                null
            }
            _uiState.update {
                it.copy(
                    isDiscogsDetailLoading = false,
                    selectedDiscogsDetail = detail,
                    userMessage = if (detail == null) "获取版本详细信息失败，请检查网络或配置 Token" else it.userMessage
                )
            }
        }
    }

    fun closeDiscogsReleaseDetail() {
        _uiState.update { it.copy(selectedDiscogsDetail = null, isDiscogsDetailLoading = false) }
    }
}
