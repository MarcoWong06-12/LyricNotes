package com.linernotes.app.data.repository

import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.core.util.HtmlUtils
import com.linernotes.app.data.local.dao.LyricAnnotationDao
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.GeniusService
import com.linernotes.app.data.remote.TranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PrewarmProgress(
    val albumId: String,
    val readyCount: Int,
    val totalCount: Int,
    val isFinished: Boolean
)

@Singleton
class AnnotationRepository @Inject constructor(
    private val lyricAnnotationDao: LyricAnnotationDao,
    private val aiPreferences: AiPreferences,
    private val translationService: TranslationService
) {

    companion object {
        private const val NEGATIVE_CACHE_DURATION_MS = 15 * 60 * 1000L // 15 分钟短效负向缓存，防止无典故歌曲频繁重复查询，同时避免网络波动造成长久死锁

        /**
         * 智能近邻权重调度：根据当前正在播放/选中的曲目索引，以向后优先、放射状排列待预热曲目
         * 顺序：[k+1, k+2, k-1, k+3, k-2, ...]
         */
        fun prioritizeTracksAround(tracks: List<TrackEntity>, currentIndex: Int): List<TrackEntity> {
            if (tracks.size <= 1) return tracks
            val safeIndex = currentIndex.coerceIn(0, tracks.lastIndex)
            return tracks
                .filterIndexed { idx, _ -> idx != safeIndex }
                .sortedWith(
                    Comparator { a, b ->
                        val idxA = tracks.indexOf(a)
                        val idxB = tracks.indexOf(b)
                        val distA = kotlin.math.abs(idxA - safeIndex)
                        val distB = kotlin.math.abs(idxB - safeIndex)
                        if (distA != distB) {
                            distA.compareTo(distB)
                        } else {
                            // Forward track (next track) gets higher priority than previous track
                            idxB.compareTo(idxA)
                        }
                    }
                )
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePrefetchAlbums = ConcurrentHashMap.newKeySet<String>()
    private val negativeCache = ConcurrentHashMap<Long, Long>()
    private val translationJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private var currentAlbumPrefetchJob: kotlinx.coroutines.Job? = null

    private val _prewarmProgressFlow = MutableStateFlow<PrewarmProgress?>(null)
    val prewarmProgressFlow: StateFlow<PrewarmProgress?> = _prewarmProgressFlow.asStateFlow()

    fun getAnnotationsFlow(trackId: Long): Flow<List<LyricAnnotationEntity>> =
        lyricAnnotationDao.getAnnotationsFlow(trackId)

    suspend fun getAnnotations(trackId: Long): List<LyricAnnotationEntity> =
        lyricAnnotationDao.getAnnotations(trackId)

    fun getSongStoryFlow(trackId: Long): Flow<SongStoryEntity?> =
        lyricAnnotationDao.getSongStoryFlow(trackId)

    suspend fun getSongStory(trackId: Long): SongStoryEntity? =
        lyricAnnotationDao.getSongStory(trackId)

    suspend fun updateSongStory(story: SongStoryEntity) = withContext(Dispatchers.IO) {
        lyricAnnotationDao.updateSongStory(story)
    }

    suspend fun updateAnnotation(annotation: LyricAnnotationEntity) = withContext(Dispatchers.IO) {
        lyricAnnotationDao.updateAnnotation(annotation)
    }

    suspend fun convertAllAnnotationsForTracks(trackIds: List<Long>, toTraditional: Boolean) = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) return@withContext
        val stories = lyricAnnotationDao.getSongStoriesForTracks(trackIds)
        for (story in stories) {
            val newStory = story.copy(
                descriptionTranslation = if (toTraditional) ChineseConverter.toTraditional(story.descriptionTranslation) else ChineseConverter.toSimplified(story.descriptionTranslation),
                descriptionPlain = if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
                    if (toTraditional) ChineseConverter.toTraditional(story.descriptionPlain) else ChineseConverter.toSimplified(story.descriptionPlain)
                } else story.descriptionPlain
            )
            lyricAnnotationDao.updateSongStory(newStory)
        }
        val annotations = lyricAnnotationDao.getAnnotationsForTracks(trackIds)
        for (annot in annotations) {
            val newAnnot = annot.copy(
                explanationTranslation = if (toTraditional) ChineseConverter.toTraditional(annot.explanationTranslation) else ChineseConverter.toSimplified(annot.explanationTranslation),
                lyricTranslation = if (toTraditional) ChineseConverter.toTraditional(annot.lyricTranslation) else ChineseConverter.toSimplified(annot.lyricTranslation),
                explanationText = if (AiAnnotationCurator.isAlreadyChinese(annot.explanationText)) {
                    if (toTraditional) ChineseConverter.toTraditional(annot.explanationText) else ChineseConverter.toSimplified(annot.explanationText)
                } else annot.explanationText
            )
            lyricAnnotationDao.updateAnnotation(newAnnot)
        }
    }


    /**
     * 将专辑加入常驻后台预取队列（近邻优先调度、多轨受控并发下载与完整双语预热）
     */
    fun enqueueAlbumPrefetch(
        albumId: String,
        artist: String,
        tracks: List<TrackEntity>,
        currentTrackIndex: Int = 0,
        forceRefresh: Boolean = false
    ) {
        if (tracks.isEmpty()) return

        currentAlbumPrefetchJob?.cancel()
        currentAlbumPrefetchJob = repositoryScope.launch {
            try {
                activePrefetchAlbums.add(albumId)
                if (forceRefresh) {
                    tracks.forEach { negativeCache.remove(it.id) }
                }

                val prioritizedTracks = prioritizeTracksAround(tracks, currentTrackIndex)
                val totalCount = tracks.size

                suspend fun countReady(): Int {
                    return tracks.count { t ->
                        val hasStory = lyricAnnotationDao.getSongStory(t.id) != null
                        val hasAnnots = lyricAnnotationDao.getAnnotations(t.id).isNotEmpty()
                        hasStory || hasAnnots
                    }
                }

                _prewarmProgressFlow.value = PrewarmProgress(
                    albumId = albumId,
                    readyCount = countReady(),
                    totalCount = totalCount,
                    isFinished = false
                )

                val semaphore = Semaphore(2)
                coroutineScope {
                    prioritizedTracks.map { track ->
                        async {
                            semaphore.withPermit {
                                try {
                                    prefetchTrackInternal(track, artist, forceRefresh)
                                    val ready = countReady()
                                    _prewarmProgressFlow.value = PrewarmProgress(
                                        albumId = albumId,
                                        readyCount = ready,
                                        totalCount = totalCount,
                                        isFinished = ready >= totalCount
                                    )
                                    delay(120)
                                } catch (e: Exception) {
                                    // 静默处理，避免干扰前台正常交互
                                }
                            }
                        }
                    }.awaitAll()
                }

                val finalReady = countReady()
                _prewarmProgressFlow.value = PrewarmProgress(
                    albumId = albumId,
                    readyCount = finalReady,
                    totalCount = totalCount,
                    isFinished = true
                )
            } finally {
                activePrefetchAlbums.remove(albumId)
            }
        }
    }

    /**
     * 后台静默预拉取全专辑曲目的 Genius 典故与背景故事（无感且极速）
     */
    suspend fun prefetchAlbumAnnotations(
        artist: String,
        tracks: List<TrackEntity>,
        currentTrackIndex: Int = 0,
        forceRefresh: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val prioritized = prioritizeTracksAround(tracks, currentTrackIndex)
        val semaphore = Semaphore(2)
        coroutineScope {
            prioritized.map { track ->
                async {
                    semaphore.withPermit {
                        try {
                            prefetchTrackInternal(track, artist, forceRefresh)
                            delay(120)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun prefetchTrackInternal(track: TrackEntity, artist: String, forceRefresh: Boolean = false) {
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        val checkedTime = negativeCache[track.id]
        val isNegCached = !forceRefresh && checkedTime != null && (System.currentTimeMillis() - checkedTime < NEGATIVE_CACHE_DURATION_MS)
        if (isNegCached) {
            return
        }

        val cachedStory = lyricAnnotationDao.getSongStory(track.id)
        val cachedAnnotations = lyricAnnotationDao.getAnnotations(track.id)
        val isGeniusData = cachedStory?.source == "GENIUS" || cachedAnnotations.any { it.source == "GENIUS" }

        if (isGeniusData && !forceRefresh) {
            // 已有 Genius 典故数据：检查是否有缺失或截断的中文对照翻译并静默补全
            val needsStoryTrans = cachedStory != null && !cachedStory.descriptionPlain.isBlank() &&
                !AiAnnotationCurator.isAlreadyChinese(cachedStory.descriptionPlain) &&
                isTruncatedTranslation(cachedStory.descriptionPlain, cachedStory.descriptionTranslation)
            val needsAnnotTrans = cachedAnnotations.any {
                !AiAnnotationCurator.isAlreadyChinese(it.explanationText) &&
                isTruncatedTranslation(it.explanationText, it.explanationTranslation)
            }
            if (needsStoryTrans || needsAnnotTrans) {
                launchBackgroundTranslation(track.id, cachedStory, cachedAnnotations, isTraditionalTarget)
            }
            return
        }

        // 本地尚无数据，或者被强制刷新：向 Genius 检索并持久化
        fetchAndCacheAnnotations(track = track, artist = artist, forceRefresh = forceRefresh)
    }

    /**
     * 从 Genius 检索、解析并本地持久化当前曲目的歌词典故与背景故事。
     * 原生 Genius 数据解析完成后即刻入库并返回界面渲染（秒级展示），
     * 中文对照翻译在后台异步平滑执行，绝不阻塞界面，不套用虚假默认模板。
     */
    suspend fun fetchAndCacheAnnotations(
        track: TrackEntity,
        artist: String,
        alignedLines: List<String> = emptyList(),
        forceRefresh: Boolean = false
    ): Result<Pair<SongStoryEntity?, List<LyricAnnotationEntity>>> = withContext(Dispatchers.IO) {
        val trackId = track.id
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        // 1. 本地 Room 缓存与负向缓存检查：
        if (forceRefresh) {
            negativeCache.remove(trackId)
        } else {
            val checkedTime = negativeCache[trackId]
            if (checkedTime != null && System.currentTimeMillis() - checkedTime < NEGATIVE_CACHE_DURATION_MS) {
                return@withContext Result.success(Pair(null, emptyList()))
            }

            val cachedAnnotations = lyricAnnotationDao.getAnnotations(trackId)
            val cachedStory = lyricAnnotationDao.getSongStory(trackId)

            // 清理历史残留的假模板数据
            if (cachedStory?.source == "AI_CURATED") {
                lyricAnnotationDao.deleteSongStoryForTrack(trackId)
            }
            if (cachedAnnotations.any { it.source == "AI_CURATED" }) {
                lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
            }

            // 自动检测并自愈损坏的缓存：若存在多条注释但引文或解说完全重复，说明历史版本因覆盖污染导致重复
            val isCorruptedDuplicateCache = cachedAnnotations.size > 1 && (
                cachedAnnotations.distinctBy { it.lyricFragment.trim() }.size == 1 ||
                cachedAnnotations.distinctBy { it.explanationText.trim() }.size == 1
            )
            if (isCorruptedDuplicateCache) {
                lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
            }

            val validCachedAnnotations = if (isCorruptedDuplicateCache) emptyList() else cachedAnnotations
            val isGeniusData = cachedStory?.source == "GENIUS" || validCachedAnnotations.any { it.source == "GENIUS" }
            if (isGeniusData && (validCachedAnnotations.isNotEmpty() || cachedStory != null)) {
                val fixedStory = if (cachedStory != null && AiAnnotationCurator.isAlreadyChinese(cachedStory.descriptionPlain) &&
                    !cachedStory.descriptionTranslation.isNullOrBlank()
                ) {
                    val s = cachedStory.copy(descriptionTranslation = null)
                    lyricAnnotationDao.updateSongStory(s)
                    s
                } else cachedStory

                // 自动排查并自愈历史损坏/截断的翻译缓存（如旧版仅截取第一行导致的残缺翻译）
                val hasTruncatedStory = fixedStory != null && isTruncatedTranslation(fixedStory.descriptionPlain, fixedStory.descriptionTranslation)
                val hasTruncatedAnnots = validCachedAnnotations.any { isTruncatedTranslation(it.explanationText, it.explanationTranslation) }
                if (hasTruncatedStory || hasTruncatedAnnots) {
                    launchBackgroundTranslation(trackId, fixedStory, validCachedAnnotations, isTraditionalTarget)
                }

                return@withContext Result.success(Pair(fixedStory, validCachedAnnotations.filter { it.source == "GENIUS" }))
            }
        }

        // 2. 尝试向 Genius 检索原生数据 (优先执行)
        val cleanTitle = GeniusService.sanitizeTitle(track.title)
        val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }

        var searchHit: com.linernotes.app.data.remote.GeniusSongSearchResult? = null
        var geniusRequestSucceeded = false
        var storyEntity: SongStoryEntity? = null
        val annotationEntities = mutableListOf<LyricAnnotationEntity>()

        try {
            searchHit = GeniusService.searchSong(
                title = cleanTitle,
                artist = artist,
                customToken = customToken
            )
            geniusRequestSucceeded = GeniusService.lastRequestConnected

            if (searchHit != null) {
                val songId = searchHit.id

                // 2.1 获取歌曲背景总览 (About this Song)
                val detail = GeniusService.getSongDetails(songId, customToken)
                if (detail != null && (detail.descriptionPlain.isNotBlank() || !detail.headerImageUrl.isNullOrBlank())) {
                    storyEntity = SongStoryEntity(
                        trackId = trackId,
                        geniusSongId = songId,
                        title = detail.title,
                        artist = detail.artist,
                        descriptionPlain = HtmlUtils.cleanPlainText(detail.descriptionPlain),
                        releaseDate = detail.releaseDate,
                        headerImageUrl = detail.headerImageUrl ?: searchHit.coverUrl,
                        songArtImageUrl = detail.songArtImageUrl ?: searchHit.coverUrl,
                        producerCredits = detail.producerCredits,
                        songUrl = detail.songUrl ?: searchHit.url,
                        source = "GENIUS"
                    )
                }

                // 2.2 获取该曲目的全部逐句歌词典故列表 (Referents)
                val referents = GeniusService.getReferents(songId, customToken)
                for (ref in referents) {
                    val primaryAnnot = ref.annotations.firstOrNull() ?: continue
                    val imagesJson = if (primaryAnnot.imageUrls.isNotEmpty()) {
                        JSONArray(primaryAnnot.imageUrls).toString()
                    } else null

                    annotationEntities.add(
                        LyricAnnotationEntity(
                            trackId = trackId,
                            lyricFragment = HtmlUtils.cleanPlainText(ref.fragment),
                            explanationText = HtmlUtils.cleanPlainText(primaryAnnot.bodyPlain),
                            authorName = primaryAnnot.authorName,
                            authorAvatarUrl = primaryAnnot.authorAvatarUrl,
                            isVerified = primaryAnnot.verified,
                            votesTotal = primaryAnnot.votesTotal,
                            imageUrlsJson = imagesJson,
                            source = "GENIUS",
                            geniusSongId = songId,
                            geniusUrl = primaryAnnot.url ?: searchHit.url
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 网络受限或 Genius 超时，不标记请求成功
        }

        // 3. 本地持久化与缓存更新：
        if (geniusRequestSucceeded) {
            val hasNewData = (storyEntity != null && storyEntity.descriptionPlain.isNotBlank()) || annotationEntities.isNotEmpty()

            if (hasNewData) {
                if (storyEntity != null) {
                    if (AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain)) {
                        storyEntity = storyEntity.copy(descriptionTranslation = null)
                    }
                    lyricAnnotationDao.insertSongStory(storyEntity)
                }

                // 原子替换该曲目的所有注释（删除旧的并插入新的，获取赋予了真实数据库自增 PrimaryKey 的实体列表）
                val persistedAnnotations = lyricAnnotationDao.replaceAnnotationsForTrack(trackId, annotationEntities)
                val savedAnnotations = if (persistedAnnotations.isNotEmpty() && persistedAnnotations.all { it.id > 0L }) {
                    persistedAnnotations
                } else {
                    lyricAnnotationDao.getAnnotations(trackId).ifEmpty { annotationEntities }
                }
                negativeCache.remove(trackId)

                // 4. 在统一受管的 repositoryScope 中执行后台异步中文对照翻译
                launchBackgroundTranslation(trackId, storyEntity, savedAnnotations, isTraditionalTarget)

                return@withContext Result.success(Pair(storyEntity, savedAnnotations))
            } else {
                // 网络已请求但未获取到新数据（搜索无结果、远端曲目无典故、或接口返回空）
                val existingStory = lyricAnnotationDao.getSongStory(trackId)
                val existingAnnots = lyricAnnotationDao.getAnnotations(trackId).filter { it.source == "GENIUS" }
                if (existingStory != null || existingAnnots.isNotEmpty()) {
                    // 保留本地既有缓存，绝不抹除用户已有典故
                    return@withContext Result.success(Pair(existingStory, existingAnnots))
                }

                if (searchHit != null) {
                    // 仅当远端确实命中曲目条目但没有任何典故时，缓存 15 分钟
                    negativeCache[trackId] = System.currentTimeMillis()
                } else {
                    negativeCache.remove(trackId)
                }
                return@withContext Result.success(Pair(null, emptyList()))
            }
        } else {
            // 请求失败（网络断开或 Genius 超时）：绝不删除本地已有缓存！
            val existingStory = lyricAnnotationDao.getSongStory(trackId)
            val existingAnnots = lyricAnnotationDao.getAnnotations(trackId).filter { it.source == "GENIUS" }
            if (existingStory != null || existingAnnots.isNotEmpty()) {
                // 回退到本地已有缓存，保障离线与弱网可用性
                return@withContext Result.success(Pair(existingStory, existingAnnots))
            }
            return@withContext Result.failure(Exception("Genius request failed due to network exception or timeout"))
        }
    }

    /**
     * 针对外文注释进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        var updated = annotation

        // 翻译歌词片段
        if (!AiAnnotationCurator.isAlreadyChinese(updated.lyricFragment)) {
            val transLyric = translationService.translateText(updated.lyricFragment, "zh")
            if (!transLyric.isNullOrBlank()) {
                val cleanedTrans = HtmlUtils.cleanTranslationOutput(transLyric)
                val finalLyric = if (isTraditionalTarget) ChineseConverter.toTraditional(cleanedTrans) else cleanedTrans
                updated = updated.copy(lyricTranslation = finalLyric)
            }
        }

        // 翻译典故解说 (无条件重新完整翻译，杜绝受历史截断数据阻塞)
        if (!AiAnnotationCurator.isAlreadyChinese(updated.explanationText)) {
            val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
            val translated = translationService.translateText(updated.explanationText, targetIso)
            val baseTrans = if (!translated.isNullOrBlank()) HtmlUtils.cleanTranslationOutput(translated) else updated.explanationText
            val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(baseTrans) else baseTrans
            updated = updated.copy(explanationTranslation = finalTrans)
        }

        lyricAnnotationDao.updateAnnotation(updated)
        updated
    }

    /**
     * 针对外文歌曲背景故事进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateSongStory(story: SongStoryEntity): SongStoryEntity = withContext(Dispatchers.IO) {
        if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
            return@withContext story
        }

        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateText(story.descriptionPlain, targetIso)
        val baseTrans = if (!translated.isNullOrBlank()) HtmlUtils.cleanTranslationOutput(translated) else story.descriptionPlain
        val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(baseTrans) else baseTrans

        val updated = story.copy(descriptionTranslation = finalTrans)
        lyricAnnotationDao.updateSongStory(updated)
        updated
    }

    private fun isTruncatedTranslation(original: String, translation: String?): Boolean {
        if (translation.isNullOrBlank()) return true
        val orig = original.trim()
        val trans = translation.trim()
        if (orig.length >= 35 && trans.length < 15) return true
        if (orig.length >= 80 && trans.length < orig.length * 0.15) return true
        return false
    }

    private fun launchBackgroundTranslation(
        trackId: Long,
        storyEntity: SongStoryEntity?,
        savedAnnotations: List<LyricAnnotationEntity>,
        isTraditionalTarget: Boolean
    ) {
        translationJobs[trackId]?.cancel()
        val job = repositoryScope.launch {
            try {
                // 1. 异步翻译背景故事（针对空翻译或历史被截断的残缺翻译）
                if (storyEntity != null && !storyEntity.descriptionPlain.isBlank() &&
                    !AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain) &&
                    isTruncatedTranslation(storyEntity.descriptionPlain, storyEntity.descriptionTranslation)
                ) {
                    try {
                        val transStory = translationService.translateText(storyEntity.descriptionPlain, "zh")
                        if (!transStory.isNullOrBlank()) {
                            val cleaned = HtmlUtils.cleanTranslationOutput(transStory)
                            val finalStoryTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                            val updatedStory = storyEntity.copy(descriptionTranslation = finalStoryTrans)
                            lyricAnnotationDao.updateSongStory(updatedStory)
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                // 2. 顺序/温和翻译各条歌词注释解说正文 (带延时防限流，优先聚焦解说实现快速中文展示)
                for (annot in savedAnnotations) {
                    try {
                        var updated = annot
                        val needsExplanationTrans = isTruncatedTranslation(annot.explanationText, annot.explanationTranslation)
                        if (!AiAnnotationCurator.isAlreadyChinese(annot.explanationText) && needsExplanationTrans) {
                            val trans = translationService.translateText(annot.explanationText, "zh")
                            if (!trans.isNullOrBlank()) {
                                val cleaned = HtmlUtils.cleanTranslationOutput(trans)
                                val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(cleaned) else cleaned
                                updated = updated.copy(explanationTranslation = finalTrans)
                            }
                        }
                        if (updated != annot && updated.id > 0L) {
                            lyricAnnotationDao.updateAnnotation(updated)
                        }
                        delay(100)
                    } catch (e: Exception) { /* ignore */ }
                }
            } finally {
                coroutineContext[kotlinx.coroutines.Job]?.let { thisJob ->
                    translationJobs.remove(trackId, thisJob)
                }
            }
        }
        translationJobs[trackId] = job
    }
}
