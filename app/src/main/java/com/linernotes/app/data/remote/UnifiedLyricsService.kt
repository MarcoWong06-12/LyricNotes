package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSanitizer
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

object UnifiedLyricsService {

    private val TIMESTAMP_REGEX = Regex("""\[\d{2}:\d{2}(?:\.\d{1,3})?\]""")

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        sourcePref: String = AiPreferences.LyricsSourcePreference.AUTO_FIRST.code,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        val pref = AiPreferences.LyricsSourcePreference.fromCode(sourcePref)

        return when (pref) {
            AiPreferences.LyricsSourcePreference.NETEASE_ONLY -> {
                val res = NetEaseLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
                res?.let { sanitizeResult(it, null) }
            }
            AiPreferences.LyricsSourcePreference.QQ_ONLY -> {
                val res = QQMusicLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
                res?.let { sanitizeResult(it, null) }
            }
            AiPreferences.LyricsSourcePreference.KUGOU_ONLY -> {
                val res = KugouLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
                res?.let { sanitizeResult(it, null) }
            }
            AiPreferences.LyricsSourcePreference.MUSIXMATCH_ONLY -> {
                MusixmatchLyricsService.fetchLyrics(trackTitle, artistName)
            }
            AiPreferences.LyricsSourcePreference.LRCLIB_ONLY -> {
                LrclibLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
            }
            AiPreferences.LyricsSourcePreference.AUTO_FIRST -> {
                fetchAutoAggregated(trackTitle, artistName, targetDurationMs)
            }
        }
    }

    /**
     * 智能多源并发竞速检索与跨源共识校验引擎 (Anti-Mismatch Consensus Engine)
     * 同步发起多源检索，通过歌曲标题关键词命中率、物理时长吻合度与跨平台演唱正文共识交叉检验，
     * 坚决剔除用户上传错误歌词、串烧混音版、同名异曲等严重错配。
     */
    private suspend fun fetchAutoAggregated(
        trackTitle: String,
        artistName: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? = supervisorScope {
        // 并发检索网易云与 QQ 音乐（具备官方双语翻译的最优源，给予 7000ms 充足检索窗口）
        val neteaseDeferred = async {
            withTimeoutOrNull(7000L) {
                NetEaseLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
            }
        }
        val qqDeferred = async {
            withTimeoutOrNull(7000L) {
                QQMusicLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
            }
        }
        // 并发检索酷狗与 LRCLIB（具备海量时间轴原版歌词的坚实后盾，给予 6500ms 窗口）
        val kugouDeferred = async {
            withTimeoutOrNull(6500L) {
                KugouLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
            }
        }
        val lrclibDeferred = async {
            withTimeoutOrNull(6500L) {
                LrclibLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
            }
        }

        // 提取标题实质关键词用于鉴伪与 Fast-Path 验证 (如 "praise", "lord", "shine")
        val keywords = LyricSearchCleaner.extractSignificantTitleKeywords(trackTitle)

        // Fast-Path: 极速抢占窗口 (最多等待 1800ms)
        // 若网易云或 QQ 音乐等官方主力源已返回高置信度双语时间轴歌词，优先校验 LRCLIB Spotify 时间轴基准后极速返回
        val fastDeadline = System.currentTimeMillis() + 1800L
        while (System.currentTimeMillis() < fastDeadline && isActive) {
            if (neteaseDeferred.isCompleted) {
                val res = runCatching { neteaseDeferred.await() }.getOrNull()
                if (res != null && isFastPathQualified(res, targetDurationMs, keywords)) {
                    val lrclib = if (lrclibDeferred.isCompleted) {
                        runCatching { lrclibDeferred.await() }.getOrNull()
                    } else {
                        withTimeoutOrNull(250L) {
                            runCatching { lrclibDeferred.await() }.getOrNull()
                        }
                    }
                    val calibrated = if (lrclib != null) calibrateWithSpotifyTiming(res, lrclib) else res
                    qqDeferred.cancel()
                    kugouDeferred.cancel()
                    lrclibDeferred.cancel()
                    return@supervisorScope sanitizeResult(calibrated, lrclib)
                }
            }
            if (qqDeferred.isCompleted) {
                val res = runCatching { qqDeferred.await() }.getOrNull()
                if (res != null && isFastPathQualified(res, targetDurationMs, keywords)) {
                    val lrclib = if (lrclibDeferred.isCompleted) {
                        runCatching { lrclibDeferred.await() }.getOrNull()
                    } else {
                        withTimeoutOrNull(250L) {
                            runCatching { lrclibDeferred.await() }.getOrNull()
                        }
                    }
                    val calibrated = if (lrclib != null) calibrateWithSpotifyTiming(res, lrclib) else res
                    neteaseDeferred.cancel()
                    kugouDeferred.cancel()
                    lrclibDeferred.cancel()
                    return@supervisorScope sanitizeResult(calibrated, lrclib)
                }
            }
            if (neteaseDeferred.isCompleted && qqDeferred.isCompleted) {
                break
            }
            delay(50L)
        }

        val neteaseRes = runCatching { neteaseDeferred.await() }.getOrNull()
        val qqRes = runCatching { qqDeferred.await() }.getOrNull()
        val kugouRes = runCatching { kugouDeferred.await() }.getOrNull()
        val lrclibRes = runCatching { lrclibDeferred.await() }.getOrNull()

        // 收集所有有效候选结果
        val candidates = listOfNotNull(neteaseRes, qqRes, kugouRes, lrclibRes)
            .filter { it.originalLyrics.isNotBlank() }

        if (candidates.isEmpty()) {
            // 若首轮并发竞速受冷启动握手或多源并发网络抢占未果，立即对直连主力源执行单路极速补抓
            val directFallback = NetEaseLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
                ?: QQMusicLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
                ?: KugouLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)
                ?: LrclibLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs)

            if (directFallback != null && directFallback.originalLyrics.isNotBlank()) {
                return@supervisorScope sanitizeResult(directFallback, null)
            }

            // 尝试轻量 Musixmatch 兜底
            return@supervisorScope withTimeoutOrNull(3000L) {
                MusixmatchLyricsService.fetchLyrics(trackTitle, artistName)
            }
        }

        // 提取各源的演唱正文指纹 (用于多源共识核验)
        val fingerprints = candidates.associateWith {
            LyricSearchCleaner.extractLyricSungFingerprint(it.originalLyrics)
        }

        // 综合鉴伪与置信度评分
        val best = candidates.maxByOrNull { res ->
            var score = 0

            // 1. 标题关键词在歌词正文中的命中度
            if (keywords.isNotEmpty()) {
                val relevance = LyricSearchCleaner.calculateTitleKeywordRelevance(res.originalLyrics, keywords)
                if (relevance > 0f) {
                    score += (relevance * 150).toInt()
                }
                // 注：许多经典曲目（如 Drake - Family Matters、Bohemian Rhapsody、晴天）
                // 歌词全文并不出现曲名，绝不进行大额扣分惩罚导致误杀
            }

            // 2. 歌曲物理时长对齐验证 (首选歌词末句时间戳，其次候选曲目元数据时长)
            val lyricLastTs = LyricSearchCleaner.extractLastTimestampMs(res.originalLyrics)
            val effectiveDuration = if (lyricLastTs > 0L) lyricLastTs else res.durationMs
            if (targetDurationMs > 0L && effectiveDuration > 0L) {
                val diffSec = Math.abs(targetDurationMs - effectiveDuration) / 1000L
                when {
                    diffSec <= 5L -> score += 120
                    diffSec <= 12L -> score += 60
                    diffSec > 25L -> score -= 250 // 时长偏差大于 25 秒，通常为不同曲目或加长版
                }
            }

            // 3. 跨源演唱指纹共识检验 (若其他数据源与本源出现相同唱词，形成极高置信度共识)
            val myFp = fingerprints[res] ?: emptyList()
            if (myFp.isNotEmpty()) {
                var consensusPeers = 0
                for ((otherRes, otherFp) in fingerprints) {
                    if (otherRes === res || otherFp.isEmpty()) continue
                    // 只要有任何 1 句核心唱词吻合，即证明并非野鸡错误歌词
                    if (myFp.any { myLine -> otherFp.any { otherLine -> myLine.contains(otherLine) || otherLine.contains(myLine) } }) {
                        consensusPeers++
                    }
                }
                score += consensusPeers * 220
            }

            // 4. 双语官方精翻超高加权 (优先采用正版人工精翻，杜绝生硬机翻)
            if (res.isBilingual && !res.translatedLyrics.isNullOrBlank()) {
                score += 350
            }

            // 5. LRC 动态同步时间戳加分
            if (res.originalLyrics.contains(TIMESTAMP_REGEX)) {
                score += 70
            }

            // 6. 敏感词被 *** 屏蔽惩罚
            if (LyricSanitizer.hasCensorship(res.originalLyrics)) {
                score -= 40
            }

            score
        }

        if (best != null) {
            // 跨源时间基准校准：若胜出源具备双语翻译，但同行中具备 Spotify/QQ 音乐高精度时间轴基准，自动进行时间戳漂移校准
            val spotifyCalibrator = lrclibRes?.takeIf { it.originalLyrics.contains(TIMESTAMP_REGEX) }
                ?: (if (best !== qqRes) qqRes?.takeIf { it.originalLyrics.contains(TIMESTAMP_REGEX) } else null)
            val calibratedWinner = if (spotifyCalibrator != null) {
                calibrateWithSpotifyTiming(best, spotifyCalibrator)
            } else best

            // 如果胜出源自身缺少封面，但同行候选源具备高清封面，智能补齐封面 URL
            val resolvedCover = calibratedWinner.coverUrl ?: candidates.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
            val enrichedWinner = if (resolvedCover != null && calibratedWinner.coverUrl == null) {
                calibratedWinner.copy(coverUrl = resolvedCover)
            } else calibratedWinner

            val reference = candidates.firstOrNull { it !== best && it.originalLyrics.isNotBlank() }
            sanitizeResult(enrichedWinner, reference)
        } else null
    }

    /**
     * 跨源时间轴交叉校验与 Spotify 原声时间基准校准：
     * 当主力源（如网易云）具备官方优质翻译，但时间戳受社区上传或音频版本影响发生漂移（> 1.2秒）时，
     * 自动保留其官方译文，而将原歌词时间轴校准为与 Spotify 音频严格吻合的基准源（如 LRCLIB 或 QQ 音乐）。
     */
    private fun calibrateWithSpotifyTiming(
        primary: OnlineLyricsResult,
        calibrator: OnlineLyricsResult
    ): OnlineLyricsResult {
        if (primary === calibrator) return primary
        if (calibrator.originalLyrics.isBlank() || !calibrator.originalLyrics.contains(TIMESTAMP_REGEX)) {
            return primary
        }
        // 若主力源没有翻译，则无需保留主力源译文直接使用即可（通常评分系统已自动择优）
        if (!primary.isBilingual || primary.translatedLyrics.isNullOrBlank()) {
            return primary
        }

        // 时长校验：若两源声明时长相差超过 15 秒，可能非同一音轨/版本，保守不予替换
        if (primary.durationMs > 0L && calibrator.durationMs > 0L) {
            val durDiff = Math.abs(primary.durationMs - calibrator.durationMs)
            if (durDiff > 15_000L) return primary
        }

        // 正文指纹与相似度校验：确保二者确实唱的是同一首歌
        val pLines = primary.originalLyrics.lines().map { LyricAligner.cleanLine(it) }.filter { it.isNotBlank() }
        val cLines = calibrator.originalLyrics.lines().map { LyricAligner.cleanLine(it) }.filter { it.isNotBlank() }
        if (pLines.isEmpty() || cLines.isEmpty()) return primary

        val pNorm = pLines.map { it.lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "") }.filter { it.isNotBlank() }
        val cNorm = cLines.map { it.lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "") }.filter { it.isNotBlank() }
        val commonCount = pNorm.count { p -> cNorm.contains(p) }
        val similarity = commonCount.toFloat() / pNorm.size.coerceAtLeast(1)
        if (similarity < 0.4f) return primary

        // 提取带时间戳的歌词行进行时间戳漂移检测
        val pWithTime = primary.originalLyrics.lines().mapNotNull { line ->
            val t = LyricAligner.extractTimestampMs(line) ?: return@mapNotNull null
            val norm = LyricAligner.cleanLine(line).lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "")
            if (norm.isNotBlank()) norm to t else null
        }
        val cWithTimeMap = calibrator.originalLyrics.lines().mapNotNull { line ->
            val t = LyricAligner.extractTimestampMs(line) ?: return@mapNotNull null
            val norm = LyricAligner.cleanLine(line).lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "")
            if (norm.isNotBlank()) norm to t else null
        }.toMap()

        val textDrifts = pWithTime.mapNotNull { (norm, pTime) ->
            val cTime = cWithTimeMap[norm] ?: return@mapNotNull null
            Math.abs(pTime - cTime)
        }

        val pTimes = primary.originalLyrics.lines().mapNotNull { LyricAligner.extractTimestampMs(it) }
        val cTimes = calibrator.originalLyrics.lines().mapNotNull { LyricAligner.extractTimestampMs(it) }
        val indexDrifts = if (pTimes.size == cTimes.size && pTimes.isNotEmpty()) {
            pTimes.zip(cTimes).map { (pt, ct) -> Math.abs(pt - ct) }
        } else emptyList()

        val maxDrift = maxOf(textDrifts.maxOrNull() ?: 0L, indexDrifts.maxOrNull() ?: 0L)
        val hasSignificantDrift = maxDrift > 1200L
        val primaryLacksLrc = !primary.originalLyrics.contains(TIMESTAMP_REGEX)

        if (hasSignificantDrift || primaryLacksLrc) {
            return primary.copy(
                originalLyrics = calibrator.originalLyrics,
                durationMs = if (primary.durationMs <= 0L) calibrator.durationMs else primary.durationMs
            )
        }
        return primary
    }

    private fun sanitizeResult(result: OnlineLyricsResult, refResult: OnlineLyricsResult?): OnlineLyricsResult {
        val hasOrigCensor = LyricSanitizer.hasCensorship(result.originalLyrics)
        val hasTransCensor = LyricSanitizer.hasCensorship(result.translatedLyrics)
        val hasTitleCensor = LyricSanitizer.hasCensorship(result.title)
        if (!hasOrigCensor && !hasTransCensor && !hasTitleCensor) {
            return result
        }
        val cleanLyrics = if (hasOrigCensor) {
            LyricSanitizer.decensorLyrics(result.originalLyrics, refResult?.originalLyrics)
        } else result.originalLyrics
        val cleanChinese = if (hasTransCensor) {
            LyricSanitizer.decensorChineseLyrics(result.translatedLyrics, cleanLyrics)
        } else result.translatedLyrics
        val cleanTitle = if (hasTitleCensor) {
            LyricSanitizer.decensorTitle(result.title, refResult?.title)
        } else result.title
        return result.copy(title = cleanTitle, originalLyrics = cleanLyrics, translatedLyrics = cleanChinese)
    }

    /**
     * 判断是否满足 Fast-Path 极速通道资格 (高置信度官方双语时间轴歌词)
     * 具备 LRC 时间戳、官方双语翻译、时长吻合（误差 <= 8秒）且标题关键词吻合时立即胜出
     */
    private fun isFastPathQualified(
        res: OnlineLyricsResult,
        targetDurationMs: Long,
        keywords: List<String>
    ): Boolean {
        if (res.originalLyrics.isBlank()) return false
        // 1. 必须具备动态 LRC 同步时间戳
        if (!res.originalLyrics.contains(TIMESTAMP_REGEX)) return false
        // 2. 必须具备双语翻译
        if (!res.isBilingual || res.translatedLyrics.isNullOrBlank()) return false
        // 3. 歌曲物理时长对齐验证 (误差 <= 6s)
        if (targetDurationMs > 0L) {
            val lyricLastTs = LyricSearchCleaner.extractLastTimestampMs(res.originalLyrics)
            val effectiveDuration = if (lyricLastTs > 0L) lyricLastTs else res.durationMs
            if (effectiveDuration > 0L) {
                val diffSec = Math.abs(targetDurationMs - effectiveDuration) / 1000L
                if (diffSec > 6L) return false
            }
        }
        // 4. 标题实质关键词核验（如果有提取到显著关键词，标题或歌词至少部分命中）
        if (keywords.isNotEmpty()) {
            val relevance = LyricSearchCleaner.calculateTitleKeywordRelevance(res.originalLyrics, keywords)
            val normResTitle = LyricSearchCleaner.normalizeForMatching(res.title)
            val titleMatches = keywords.any { kw ->
                normResTitle.contains(LyricSearchCleaner.normalizeForMatching(kw))
            }
            if (relevance <= 0f && !titleMatches) return false
        }
        return true
    }

    /**
     * 手动搜索歌词版本候选（跨网易云/QQ音乐并发搜索，带时长差异与双语标记智能排序）
     */
    suspend fun searchLyricCandidates(
        query: String,
        targetDurationMs: Long = 0L
    ): List<com.linernotes.app.domain.model.LyricCandidateItem> = supervisorScope {
        if (query.isBlank()) return@supervisorScope emptyList()

        val neteaseDeferred = async {
            runCatching { NetEaseLyricsService.searchRawCandidates(query, limit = 8) }.getOrDefault(emptyList())
        }
        val qqDeferred = async {
            runCatching { QQMusicLyricsService.searchRawCandidates(query, limit = 8) }.getOrDefault(emptyList())
        }

        val neteaseList = neteaseDeferred.await()
        val qqList = qqDeferred.await()

        val combined = (neteaseList + qqList).toMutableList()

        // 智能排序：
        // 1. 若有时长匹配，时长误差 <= 4s 的排前面
        // 2. 有双语翻译标记的排前面
        combined.sortWith(
            compareByDescending<com.linernotes.app.domain.model.LyricCandidateItem> { item ->
                var weight = 0
                if (item.hasTranslation) weight += 100
                if (targetDurationMs > 0L && item.durationMs > 0L) {
                    val diffSec = Math.abs(targetDurationMs - item.durationMs) / 1000L
                    if (diffSec <= 3L) weight += 200
                    else if (diffSec <= 8L) weight += 120
                    else if (diffSec <= 15L) weight += 60
                }
                weight
            }
        )

        combined
    }

    /**
     * 精确拉取指定候选版本的在线歌词与双语翻译
     */
    suspend fun fetchLyricForCandidate(
        candidate: com.linernotes.app.domain.model.LyricCandidateItem
    ): OnlineLyricsResult? {
        val result = when (candidate.source) {
            com.linernotes.app.domain.model.LyricSource.NETEASE -> {
                val songId = candidate.sourceId.toLongOrNull() ?: return null
                NetEaseLyricsService.fetchLyricById(songId, candidate.title, candidate.artist)
            }
            com.linernotes.app.domain.model.LyricSource.QQ_MUSIC -> {
                QQMusicLyricsService.fetchLyricByMid(
                    songmid = candidate.sourceId,
                    fallbackTitle = candidate.title,
                    fallbackArtist = candidate.artist,
                    albummid = candidate.extraKey,
                    durationMs = candidate.durationMs
                )
            }
        }
        return result?.let { sanitizeResult(it, null) }
    }
}
