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

    // 内存高速缓存 (同一首歌二次播放 0ms 响应)
    private val memoryCache = mutableMapOf<String, CachedSongData>()

    private data class CachedSongData(
        val lyrics: List<BilingualLyricLine>,
        val annotatedLines: Map<Int, LyricAnnotationEntity>,
        val songStory: SongStoryEntity?
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
        _nowPlayingData.value = current.copy(playbackState = state)

        if (!state.hasValidTrack) return

        val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
        if (songKey != currentSongKey) {
            currentSongKey = songKey
            loadSongLyricsAndGenius(state.title, state.artist, songKey)
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = repositoryScope.launch {
            while (isActive) {
                val current = _nowPlayingData.value
                val state = current.playbackState
                if (state.isPlaying && current.lyrics.isNotEmpty()) {
                    val currentPos = state.getEstimatedPositionMs()
                    val lineIndex = calculateActiveLineIndex(current.lyrics, currentPos)
                    if (lineIndex != current.currentLineIndex) {
                        _nowPlayingData.value = current.copy(currentLineIndex = lineIndex)
                    }
                }
                delay(100) // 100ms 刷新周期，平滑捕捉时间轴变更
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
        return activeIndex
    }

    fun loadSongLyricsAndGenius(title: String, artist: String, songKey: String) {
        dataLoadJob?.cancel()

        // 检查内存缓存
        val cached = memoryCache[songKey]
        if (cached != null) {
            _nowPlayingData.update {
                it.copy(
                    lyrics = cached.lyrics,
                    annotatedLines = cached.annotatedLines,
                    songStory = cached.songStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false,
                    errorMessage = null
                )
            }
            return
        }

        _nowPlayingData.update {
            it.copy(
                lyrics = emptyList(),
                annotatedLines = emptyMap(),
                songStory = null,
                isLoadingLyrics = true,
                isLoadingGenius = true,
                errorMessage = null
            )
        }

        dataLoadJob = repositoryScope.launch {
            var fetchedLyrics: List<BilingualLyricLine> = emptyList()
            var fetchedStory: SongStoryEntity? = null
            var fetchedAnnotations: List<LyricAnnotationEntity> = emptyList()

            // 1. 并发抓取歌词与自动翻译补全
            val lyricsJob = async {
                try {
                    val rawResult = UnifiedLyricsService.fetchLyrics(
                        trackTitle = title,
                        artistName = artist,
                        sourcePref = aiPreferences.lyricsSourcePreference
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
                        val cleanOrig = LyricSanitizer.sanitizeLyrics(origLyrics, null)
                        val cleanTrans = transLyrics?.let { LyricSanitizer.sanitizeLyrics(it, null) }

                        // 结构化时间轴对齐
                        fetchedLyrics = LyricAligner.align(cleanOrig, cleanTrans)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 并发抓取 Genius 歌曲创作背景与歌词行典故
            val geniusJob = async {
                try {
                    val searchResult = GeniusService.searchSong(title, artist)
                    if (searchResult != null) {
                        val songId = searchResult.id

                        // 抓取整曲背景故事
                        val songDetail = GeniusService.fetchSongDetail(songId)
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
                        val referents = GeniusService.fetchReferents(songId)
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

            // 将 Genius 典故通过片段匹配器锚定到歌词行
            val matchedAnnotations = if (fetchedLyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
                LyricFragmentMatcher.matchAnnotationsToLines(fetchedLyrics, fetchedAnnotations)
            } else {
                emptyMap()
            }

            // 写入内存缓存
            val cachedData = CachedSongData(
                lyrics = fetchedLyrics,
                annotatedLines = matchedAnnotations,
                songStory = fetchedStory
            )
            memoryCache[songKey] = cachedData

            _nowPlayingData.update {
                it.copy(
                    lyrics = fetchedLyrics,
                    annotatedLines = matchedAnnotations,
                    songStory = fetchedStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false
                )
            }
        }
    }

    // 播控方法代理回传 Spotify
    fun play() = playbackStateManager.play()
    fun pause() = playbackStateManager.pause()
    fun togglePlayPause() = playbackStateManager.togglePlayPause()
    fun skipToNext() = playbackStateManager.skipToNext()
    fun skipToPrevious() = playbackStateManager.skipToPrevious()
    fun seekTo(positionMs: Long) = playbackStateManager.seekTo(positionMs)
}
