package com.linernotes.app.data.repository

import android.os.SystemClock
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricFragmentMatcher
import com.linernotes.app.core.lyric.LyricSanitizer
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.playback.PlaybackStateManager
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
import com.linernotes.app.data.remote.NetEaseLyricsService
import com.linernotes.app.data.remote.QQMusicLyricsService
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

    val lyricOffsetMsFlow: StateFlow<Long> = floatingPreferences.lyricOffsetMsFlow
    private var lyricOffsetMs: Long = 0L

    fun setLyricOffset(offsetMs: Long) {
        lyricOffsetMs = offsetMs
        floatingPreferences.lyricOffsetMs = offsetMs
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

        // 3. 初始载入并动态监听全局歌词时间轴偏移 (默认 -200ms 音频与蓝牙硬件延迟补偿)
        lyricOffsetMs = floatingPreferences.lyricOffsetMs
        repositoryScope.launch {
            floatingPreferences.lyricOffsetMsFlow.collect { offset ->
                lyricOffsetMs = offset
            }
        }
    }

    private fun handlePlaybackStateUpdate(state: TrackPlaybackState) {
        _nowPlayingData.update { current ->
            val activeLine = if (current.lyrics.isNotEmpty()) {
                val currentPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                calculateActiveLineIndex(current.lyrics, currentPos)
            } else current.currentLineIndex

            current.copy(
                playbackState = state,
                currentLineIndex = activeLine
            )
        }

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

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = repositoryScope.launch {
            while (isActive) {
                val current = _nowPlayingData.value
                val state = current.playbackState
                if (current.lyrics.isNotEmpty() && state.hasValidTrack && state.isPlaying) {
                    val currentPos = (state.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                    val lineIndex = calculateActiveLineIndex(current.lyrics, currentPos)
                    if (lineIndex != current.currentLineIndex) {
                        _nowPlayingData.update { latest ->
                            if (latest.lyrics.isNotEmpty() && latest.currentLineIndex != lineIndex) {
                                latest.copy(currentLineIndex = lineIndex)
                            } else latest
                        }
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
        val cached = memoryCache[songKey]
        if (cached != null) {
            // 清理歌词缓存但保留已抓取的 Genius 背景故事，防止重复拉取 Genius
            memoryCache[songKey] = cached.copy(lyrics = emptyList(), annotatedLines = emptyMap())
        } else {
            memoryCache.remove(songKey)
        }
        _nowPlayingData.update {
            it.copy(
                isLoadingLyrics = true,
                errorMessage = null
            )
        }
        loadSongLyricsAndGenius(title, artist, songKey)
    }

    fun loadSongLyricsAndGenius(title: String, artist: String, songKey: String) {
        dataLoadJob?.cancel()
        noticeDismissJob?.cancel()

        // 检查内存缓存：只有当歌词具备且（典故已具备 或 失败通知已结算）时，才视为完全命中缓存直接秒开
        val cached = memoryCache[songKey]
        if (cached != null && cached.lyrics.isNotEmpty() && (cached.annotatedLines.isNotEmpty() || cached.songStory != null || cached.geniusNotice != null)) {
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

        // 若歌词已就绪但典故尚未获取，立刻展示歌词，后台并发补全典故
        if (cached != null && cached.lyrics.isNotEmpty()) {
            _nowPlayingData.update {
                it.copy(
                    lyrics = cached.lyrics,
                    annotatedLines = cached.annotatedLines,
                    songStory = cached.songStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = cached.songStory == null && cached.geniusNotice == null,
                    geniusNoticeMessage = cached.geniusNotice,
                    errorMessage = null
                )
            }
        } else {
            // 歌词尚未就绪（或上次抓取落空），保留已有故事避免界面跳闪，进入歌词加载态
            _nowPlayingData.update {
                it.copy(
                    lyrics = emptyList(),
                    annotatedLines = emptyMap(),
                    songStory = cached?.songStory,
                    isLoadingLyrics = true,
                    isLoadingGenius = cached?.songStory == null,
                    geniusNoticeMessage = cached?.geniusNotice,
                    errorMessage = null
                )
            }
        }

        dataLoadJob = repositoryScope.launch {
            var fetchedLyrics: List<BilingualLyricLine> = cached?.lyrics ?: emptyList()
            var fetchedStory: SongStoryEntity? = cached?.songStory
            var fetchedAnnotations: List<LyricAnnotationEntity> = emptyList()

            // 1. 若歌词尚未就绪，并发抓取歌词与自动翻译补全
            val lyricsJob = async {
                if (fetchedLyrics.isNotEmpty()) return@async
                try {
                    val curState = playbackStateManager.playbackState.value
                    var rawResult = UnifiedLyricsService.fetchLyrics(
                        trackTitle = title,
                        artistName = artist,
                        sourcePref = aiPreferences.lyricsSource,
                        targetDurationMs = curState.durationMs
                    )

                    // 如果首次抓取落空（例如冷启动多源并发争抢导致超时），延时 300ms 进行一次直接直连兜底
                    if (rawResult == null || rawResult.originalLyrics.isBlank()) {
                        kotlinx.coroutines.delay(300)
                        rawResult = NetEaseLyricsService.fetchLyrics(title, artist, curState.durationMs)
                            ?: QQMusicLyricsService.fetchLyrics(title, artist, curState.durationMs)
                            ?: KugouLyricsService.fetchLyrics(title, artist, curState.durationMs)
                            ?: LrclibLyricsService.fetchLyrics(title, artist, curState.durationMs)
                    }

                    if (rawResult != null && rawResult.originalLyrics.isNotBlank()) {
                        // 回填从歌词源中获取的高清官方专辑封面
                        val lyricCover = rawResult.coverUrl
                        if (!lyricCover.isNullOrBlank()) {
                            val cur = playbackStateManager.playbackState.value
                            if (cur.coverUrl.isNullOrBlank() || cur.coverUrl.startsWith("/")) {
                                playbackStateManager.updateState(cur.copy(coverUrl = lyricCover))
                            }
                        }

                        var origLyrics = rawResult.originalLyrics
                        var transLyrics = rawResult.translatedLyrics

                        // 如果抓取到的歌词没有中文翻译，优先向网易云与 QQ 音乐查询官方人工双语精翻！
                        if (transLyrics.isNullOrBlank()) {
                            try {
                                val officialBilingual = NetEaseLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                    ?: QQMusicLyricsService.fetchLyrics(title, artist, curState.durationMs)
                                if (officialBilingual != null && !officialBilingual.translatedLyrics.isNullOrBlank()) {
                                    origLyrics = officialBilingual.originalLyrics
                                    transLyrics = officialBilingual.translatedLyrics
                                }
                            } catch (e: Exception) {
                                // 忽略
                            }
                        }

                        // 若官方平台均无人工译文，触发内置极速翻译引擎（带序号硬锚定，零错位）
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
            var currentSongId: Long? = null
            val geniusJob = async {
                try {
                    val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }
                    val searchResult = GeniusService.searchSong(title, artist, customToken)
                    if (searchResult != null) {
                        geniusSongFound = true
                        val songId = searchResult.id
                        currentSongId = songId

                        // 回填云端超高清专辑封面
                        val cloudCover = searchResult.coverUrl ?: searchResult.thumbUrl
                        if (!cloudCover.isNullOrBlank()) {
                            val cur = playbackStateManager.playbackState.value
                            if (cur.coverUrl.isNullOrBlank() || cur.coverUrl.startsWith("/")) {
                                playbackStateManager.updateState(cur.copy(coverUrl = cloudCover))
                            }
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

            // 等待并发抓取完成
            lyricsJob.await()
            _nowPlayingData.update {
                it.copy(
                    lyrics = fetchedLyrics,
                    isLoadingLyrics = false
                )
            }

            geniusJob.await()

            // 阶段一：即刻渲染原生典故徽标与背景故事按钮（1秒级展示，绝不让用户等待逐条翻译完成）
            val initialMatched = if (fetchedLyrics.isNotEmpty() && fetchedAnnotations.isNotEmpty()) {
                LyricFragmentMatcher.matchAnnotationsToLines(fetchedLyrics, fetchedAnnotations)
            } else {
                emptyMap()
            }

            val currentStory = fetchedStory
            // 获取失败精准提示：当 Genius 典故与背景故事未能获取时，生成清晰诚实的通知提醒用户
            val failureNotice: String? = when {
                initialMatched.isNotEmpty() || (currentStory != null && currentStory.descriptionPlain.isNotBlank()) -> null
                !GeniusService.lastRequestConnected -> "Genius 网络连接受阻，无法获取典故"
                !geniusSongFound -> "未在 Genius 检索到本曲收录"
                fetchedAnnotations.isEmpty() -> "Genius 尚未收录本曲歌词典故"
                else -> "Genius 典故与当前歌词版本未能成功匹配"
            }

            // 写入内存缓存（未翻译版本立即可供点击，仅当歌词有效时才缓存，防止空歌词锁死缓存）
            if (fetchedLyrics.isNotEmpty()) {
                val preliminaryData = CachedSongData(
                    lyrics = fetchedLyrics,
                    annotatedLines = initialMatched,
                    songStory = fetchedStory,
                    geniusNotice = failureNotice
                )
                memoryCache[songKey] = preliminaryData
            }

            _nowPlayingData.update {
                it.copy(
                    lyrics = fetchedLyrics,
                    annotatedLines = initialMatched,
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

            // 阶段二：后台受控并发极速补全中文对照翻译 (4 协程并行，1.5 秒内全部就绪并平滑刷新界面)
            if (fetchedStory != null || fetchedAnnotations.isNotEmpty()) {
                val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
                val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
                    TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW
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
                                    if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(tr))) {
                                        val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                                        val finalStoryTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                        fetchedStory = cStory.copy(descriptionTranslation = finalStoryTrans)
                                    }
                                } catch (e: Exception) { }
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
                                        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(tr))) {
                                            val cleaned = HtmlUtils.cleanTranslationOutput(tr)
                                            val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                            annot.copy(explanationTranslation = finalTrans)
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

                // 翻译就绪，更新界面与高速内存缓存
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
                        geniusNotice = failureNotice
                    )
                }

                _nowPlayingData.update {
                    it.copy(
                        annotatedLines = finalMatched,
                        songStory = fetchedStory
                    )
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
                key != currentKey && (cached == null || (cached.annotatedLines.isEmpty() && cached.songStory == null))
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
        var lyrics = memoryCache[songKey]?.lyrics ?: emptyList()
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

            // 如果抓取到的歌词没有中文翻译，优先向网易云与 QQ 音乐查询官方人工双语精翻！
            if (transLyrics.isNullOrBlank()) {
                try {
                    val officialBilingual = NetEaseLyricsService.fetchLyrics(title, artist)
                        ?: QQMusicLyricsService.fetchLyrics(title, artist)
                    if (officialBilingual != null && !officialBilingual.translatedLyrics.isNullOrBlank()) {
                        origLyrics = officialBilingual.originalLyrics
                        transLyrics = officialBilingual.translatedLyrics
                    }
                } catch (e: Exception) {
                    // 忽略
                }
            }

            // 若官方平台均无人工译文，触发内置极速翻译引擎（带序号硬锚定，零错位）
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
                    TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW
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
                                    if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(tr))) {
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
                                        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(tr))) {
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
            geniusNotice = null
        )
        memoryCache[songKey] = cached

        // 若当前播放中的歌曲恰好是预加载完成的曲目，实时刷新界面
        if (currentSongKey == songKey) {
            _nowPlayingData.update {
                it.copy(
                    lyrics = lyrics,
                    annotatedLines = matchedAnnotations,
                    songStory = fetchedStory,
                    isLoadingLyrics = false,
                    isLoadingGenius = false
                )
            }
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
        if (current.isLoadingGenius) return

        val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
        val existingLyrics = memoryCache[songKey]?.lyrics ?: current.lyrics

        // 立即进入加载中状态并更新通知，向用户提供即时正反馈
        val loadingNotice = "正在检索 Genius 典故..."
        _nowPlayingData.update {
            it.copy(
                isLoadingGenius = true,
                geniusNoticeMessage = loadingNotice
            )
        }

        if (existingLyrics.isNotEmpty()) {
            memoryCache[songKey] = CachedSongData(
                lyrics = existingLyrics,
                annotatedLines = emptyMap(),
                songStory = null,
                geniusNotice = loadingNotice
            )
        } else {
            memoryCache.remove(songKey)
        }
        loadSongLyricsAndGenius(state.title, state.artist, songKey)
    }

    suspend fun translateSingleAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        if (AiAnnotationCurator.isAlreadyChinese(annotation.explanationText)) {
            return@withContext annotation
        }
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        val tr = translationService.translateText(annotation.explanationText, targetIso)
        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(tr))) {
            val cleaned = HtmlUtils.cleanTranslationOutput(tr)
            val finalTr = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
            val updated = annotation.copy(explanationTranslation = finalTr)

            _nowPlayingData.update { current ->
                val newAnnotatedLines = current.annotatedLines.mapValues { (_, v) ->
                    if (v.id == annotation.id || (v.lyricFragment == annotation.lyricFragment && v.explanationText == annotation.explanationText)) updated else v
                }
                current.copy(annotatedLines = newAnnotatedLines)
            }

            val curKey = currentSongKey
            if (curKey != null) {
                memoryCache[curKey]?.let { cached ->
                    val updatedCache = cached.copy(
                        annotatedLines = cached.annotatedLines.mapValues { (_, v) ->
                            if (v.id == annotation.id || (v.lyricFragment == annotation.lyricFragment && v.explanationText == annotation.explanationText)) updated else v
                        }
                    )
                    memoryCache[curKey] = updatedCache
                }
            }
            return@withContext updated
        }
        return@withContext annotation
    }

    suspend fun translateCurrentStory(story: SongStoryEntity): SongStoryEntity = withContext(Dispatchers.IO) {
        if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
            return@withContext story
        }
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        val tr = translationService.translateText(story.descriptionPlain, targetIso)
        if (!tr.isNullOrBlank() && (AiAnnotationCurator.isAlreadyChinese(tr) || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(tr))) {
            val cleaned = HtmlUtils.cleanTranslationOutput(tr)
            val finalTr = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
            val updated = story.copy(descriptionTranslation = finalTr)

            _nowPlayingData.update { current ->
                current.copy(songStory = updated)
            }

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
}
