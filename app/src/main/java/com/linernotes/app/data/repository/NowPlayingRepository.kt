package com.linernotes.app.data.repository

import android.os.SystemClock
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricFragmentMatcher
import com.linernotes.app.core.lyric.LyricSanitizer
import com.linernotes.app.core.playback.PlaybackStateManager
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.remote.GeniusService
import com.linernotes.app.data.remote.TranslationService
import com.linernotes.app.data.remote.UnifiedLyricsService
import com.linernotes.app.domain.model.BilingualLyricLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

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
    private val aiPreferences: AiPreferences
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _nowPlayingData = MutableStateFlow(NowPlayingData())
    val nowPlayingData: StateFlow<NowPlayingData> = _nowPlayingData.asStateFlow()

    private var currentSongKey: String? = null
    private var dataLoadJob: Job? = null
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
        val geniusNotice: String? = null
    )

    init {
        // 1. 监听 Spotify 播放状态变更
        repositoryScope.launch {
            playbackStateManager.playbackState.collect { state ->
                handlePlaybackStateUpdate(state)
            }
        }

        // 2. 毫秒级时间轴驱动与高亮行定位 (60fps 级流畅推算)
        startPositionTracker()
    }

    private fun handlePlaybackStateUpdate(state: TrackPlaybackState) {
        val current = _nowPlayingData.value
        val activeLine = if (current.lyrics.isNotEmpty()) {
            val currentPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            calculateActiveLineIndex(current.lyrics, currentPos)
        } else current.currentLineIndex

        _nowPlayingData.value = current.copy(
            playbackState = state,
            currentLineIndex = activeLine
        )

        if (!state.hasValidTrack) return

        val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
        if (songKey != currentSongKey) {
            currentSongKey = songKey
            loadSongLyricsAndGenius(state.title, state.artist, songKey)
        }

        // 待播队列静默预加载：提取队列下首曲目，提前静默拉取歌词与翻译并存入内存缓存
        if (state.queueItems.isNotEmpty()) {
            preloadNextTracks(state.queueItems)
        }
    }

    private var lyricOffsetMs: Long = 0L

    fun setLyricOffset(offsetMs: Long) {
        lyricOffsetMs = offsetMs
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = repositoryScope.launch {
            while (isActive) {
                val current = _nowPlayingData.value
                val state = current.playbackState
                if (current.lyrics.isNotEmpty() && state.hasValidTrack) {
                    val currentPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                    val lineIndex = calculateActiveLineIndex(current.lyrics, currentPos)
                    if (lineIndex != current.currentLineIndex) {
                        _nowPlayingData.value = current.copy(currentLineIndex = lineIndex)
                    }
                }
                delay(50) // 50ms 刷新周期，平滑灵敏捕捉
            }
        }
    }

    private fun calculateActiveLineIndex(lyrics: List<BilingualLyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var activeIndex = -1
        for (i in lyrics.indices) {
            val startTime = lyrics[i].startTimeMs ?: continue
            if (startTime <= positionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        // 如果处于曲目前奏期 (positionMs 小于第 1 句歌词时间)，聚焦首句，避免全屏歌词落入非活跃暗色
        if (activeIndex == -1 && lyrics.isNotEmpty()) {
            return 0
        }
        return activeIndex
    }

    fun forceReloadLyrics(title: String, artist: String, songKey: String) {
        memoryCache.remove(songKey)
        loadSongLyricsAndGenius(title, artist, songKey)
    }

    fun loadSongLyricsAndGenius(title: String, artist: String, songKey: String) {
        dataLoadJob?.cancel()
        noticeDismissJob?.cancel()

        // 检查内存缓存：若歌词和典故都已具备，直接秒开
        val cached = memoryCache[songKey]
        if (cached != null && (cached.annotatedLines.isNotEmpty() || cached.songStory != null)) {
            _nowPlayingData.update {
                it.copy(
                    lyrics = cached.lyrics,
                    annotatedLines = cached.annotatedLines,
                    songStory = cached.songStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false,
                    geniusNoticeMessage = cached.geniusNotice,
                    errorMessage = null
                )
            }
            return
        }

        // 若歌词已预加载但典故尚未获取，立刻展示歌词，后台并发补全典故
        if (cached != null && cached.lyrics.isNotEmpty()) {
            _nowPlayingData.update {
                it.copy(
                    lyrics = cached.lyrics,
                    annotatedLines = emptyMap(),
                    songStory = null,
                    isLoadingLyrics = false,
                    isLoadingGenius = true,
                    geniusNoticeMessage = null,
                    errorMessage = null
                )
            }
        } else {
            _nowPlayingData.update {
                it.copy(
                    lyrics = emptyList(),
                    annotatedLines = emptyMap(),
                    songStory = null,
                    isLoadingLyrics = true,
                    isLoadingGenius = true,
                    geniusNoticeMessage = null,
                    errorMessage = null
                )
            }
        }

        dataLoadJob = repositoryScope.launch {
            var fetchedLyrics: List<BilingualLyricLine> = cached?.lyrics ?: emptyList()
            var fetchedStory: SongStoryEntity? = null
            var fetchedAnnotations: List<LyricAnnotationEntity> = emptyList()

            // 1. 若歌词尚未就绪，并发抓取歌词与自动翻译补全
            val lyricsJob = async {
                if (fetchedLyrics.isNotEmpty()) return@async
                try {
                    val rawResult = UnifiedLyricsService.fetchLyrics(
                        trackTitle = title,
                        artistName = artist,
                        sourcePref = aiPreferences.lyricsSource
                    )

                    if (rawResult != null && rawResult.originalLyrics.isNotBlank()) {
                        var origLyrics = rawResult.originalLyrics
                        var transLyrics = rawResult.translatedLyrics

                        // 如果抓取到的歌词没有中文翻译，触发内置极速翻译引擎！
                        if (transLyrics.isNullOrBlank()) {
                            try {
                                val transResult = translationService.translateTrack(title, origLyrics)
                                if (transResult != null && transResult.translatedLyrics.isNotBlank()) {
                                    transLyrics = transResult.translatedLyrics
                                }
                            } catch (e: Exception) {
                                // 容灾降级
                            }
                        }

                        // 脏字脱敏反屏蔽处理
                        val cleanOrig = LyricSanitizer.decensorLyrics(origLyrics)
                        val cleanTrans = LyricSanitizer.decensorChineseLyrics(transLyrics, cleanOrig)

                        // 结构化时间轴对齐
                        fetchedLyrics = LyricAligner.align(cleanOrig, cleanTrans)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 并发抓取 Genius 歌曲创作背景与歌词行典故
            var geniusSongFound = false
            val geniusJob = async {
                try {
                    val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }
                    val searchResult = GeniusService.searchSong(title, artist, customToken)
                    if (searchResult != null) {
                        geniusSongFound = true
                        val songId = searchResult.id

                        // 回填云端超高清专辑封面
                        val cloudCover = searchResult.coverUrl ?: searchResult.thumbUrl
                        if (!cloudCover.isNullOrBlank()) {
                            val cur = playbackStateManager.playbackState.value
                            if (cur.coverUrl.isNullOrBlank() || cur.coverUrl.startsWith("/")) {
                                playbackStateManager.updateState(cur.copy(coverUrl = cloudCover))
                            }
                        }

                        // 抓取整曲背景故事
                        val songDetail = GeniusService.getSongDetails(songId, customToken)
                        if (songDetail != null) {
                            var transDesc: String? = null
                            if (songDetail.descriptionPlain.isNotBlank()) {
                                try {
                                    val transRes = translationService.translateTrack("Story", songDetail.descriptionPlain)
                                    transDesc = transRes?.translatedLyrics
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                            fetchedStory = SongStoryEntity(
                                trackId = songId,
                                geniusSongId = songId,
                                title = songDetail.title,
                                artist = songDetail.artist,
                                descriptionPlain = songDetail.descriptionPlain,
                                descriptionTranslation = transDesc,
                                releaseDate = songDetail.releaseDate,
                                headerImageUrl = songDetail.headerImageUrl,
                                songArtImageUrl = songDetail.songArtImageUrl,
                                producerCredits = songDetail.producerCredits,
                                songUrl = songDetail.songUrl
                            )
                        }

                        // 抓取歌词行内典故
                        val referents = GeniusService.getReferents(songId, customToken)
                        val annotList = mutableListOf<LyricAnnotationEntity>()
                        for (ref in referents) {
                            val annotItem = ref.annotations.firstOrNull() ?: continue
                            var explTrans: String? = null
                            if (annotItem.bodyPlain.isNotBlank()) {
                                try {
                                    val tRes = translationService.translateTrack("Annotation", annotItem.bodyPlain)
                                    explTrans = tRes?.translatedLyrics
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                            annotList.add(
                                LyricAnnotationEntity(
                                    trackId = songId,
                                    lyricFragment = ref.fragment,
                                    explanationText = annotItem.bodyPlain,
                                    authorName = annotItem.authorName,
                                    authorAvatarUrl = annotItem.authorAvatarUrl,
                                    isVerified = annotItem.verified,
                                    votesTotal = annotItem.votesTotal,
                                    explanationTranslation = explTrans,
                                    geniusSongId = songId,
                                    geniusUrl = annotItem.url
                                )
                            )
                        }
                        fetchedAnnotations = annotList
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 等待并发抓取完成
            lyricsJob.await()
            _nowPlayingData.update {
                it.copy(
                    lyrics = fetchedLyrics,
                    isLoadingLyrics = false
                )
            }

            geniusJob.await()

            // 真实典故片段匹配：仅将 Genius 官方典故精确锚定到歌词行，坚决不伪造或自动生成虚假内容
            val matchedAnnotations = if (fetchedLyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
                LyricFragmentMatcher.matchAnnotationsToLines(fetchedLyrics, fetchedAnnotations)
            } else {
                emptyMap()
            }

            // 获取失败精准提示：当 Genius 典故与背景故事未能获取时，生成清晰诚实的通知提醒用户
            val failureNotice: String? = when {
                matchedAnnotations.isNotEmpty() || (fetchedStory != null && fetchedStory.descriptionPlain.isNotBlank()) -> null
                !GeniusService.lastRequestConnected -> "Genius 网络连接受阻，无法获取典故"
                !geniusSongFound -> "未在 Genius 检索到本曲收录"
                fetchedAnnotations.isEmpty() -> "Genius 尚未收录本曲歌词典故"
                else -> "Genius 典故与当前歌词版本未能成功匹配"
            }

            // 写入内存缓存
            val cachedData = CachedSongData(
                lyrics = fetchedLyrics,
                annotatedLines = matchedAnnotations,
                songStory = fetchedStory,
                geniusNotice = failureNotice
            )
            memoryCache[songKey] = cachedData

            _nowPlayingData.update {
                it.copy(
                    lyrics = fetchedLyrics,
                    annotatedLines = matchedAnnotations,
                    songStory = fetchedStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false,
                    geniusNoticeMessage = failureNotice
                )
            }

            // 若有失败提醒，7 秒后自动淡化提示，避免持续遮挡歌词
            if (failureNotice != null) {
                noticeDismissJob?.cancel()
                noticeDismissJob = repositoryScope.launch {
                    delay(7000)
                    _nowPlayingData.update {
                        if (it.geniusNoticeMessage == failureNotice) it.copy(geniusNoticeMessage = null) else it
                    }
                }
            }
        }
    }

    private var preloadingJob: Job? = null

    private fun preloadNextTracks(queueItems: List<com.linernotes.app.core.playback.QueueTrackItem>) {
        if (queueItems.isEmpty()) return
        preloadingJob?.cancel()
        preloadingJob = repositoryScope.launch {
            val candidates = queueItems.take(2)
            for (item in candidates) {
                if (item.title.isBlank() || item.artist.isBlank()) continue
                val nextKey = "${item.artist.trim().lowercase()} - ${item.title.trim().lowercase()}"
                if (memoryCache.containsKey(nextKey)) continue

                try {
                    silentFetchAndCacheLyrics(item.title, item.artist, nextKey)
                } catch (e: Exception) {
                    // 静默容灾
                }
            }
        }
    }

    private suspend fun silentFetchAndCacheLyrics(title: String, artist: String, songKey: String) {
        val rawResult = UnifiedLyricsService.fetchLyrics(
            trackTitle = title,
            artistName = artist,
            sourcePref = aiPreferences.lyricsSource
        ) ?: return

        if (rawResult.originalLyrics.isBlank()) return

        var origLyrics = rawResult.originalLyrics
        var transLyrics = rawResult.translatedLyrics

        if (transLyrics.isNullOrBlank()) {
            try {
                val transResult = translationService.translateTrack(title, origLyrics)
                if (transResult != null && transResult.translatedLyrics.isNotBlank()) {
                    transLyrics = transResult.translatedLyrics
                }
            } catch (e: Exception) {
                // 忽略
            }
        }

        val cleanOrig = LyricSanitizer.decensorLyrics(origLyrics)
        val cleanTrans = LyricSanitizer.decensorChineseLyrics(transLyrics, cleanOrig)
        val aligned = LyricAligner.align(cleanOrig, cleanTrans)

        if (aligned.isNotEmpty()) {
            memoryCache[songKey] = CachedSongData(
                lyrics = aligned,
                annotatedLines = emptyMap(),
                songStory = null
            )
        }
    }

    fun dismissGeniusNotice() {
        noticeDismissJob?.cancel()
        _nowPlayingData.update { it.copy(geniusNoticeMessage = null) }
    }

    fun reloadGeniusOnly() {
        val current = _nowPlayingData.value
        val state = current.playbackState
        if (!state.hasValidTrack) return
        val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
        val existingLyrics = memoryCache[songKey]?.lyrics ?: current.lyrics
        if (existingLyrics.isNotEmpty()) {
            memoryCache[songKey] = CachedSongData(
                lyrics = existingLyrics,
                annotatedLines = emptyMap(),
                songStory = null,
                geniusNotice = null
            )
        } else {
            memoryCache.remove(songKey)
        }
        loadSongLyricsAndGenius(state.title, state.artist, songKey)
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
}
