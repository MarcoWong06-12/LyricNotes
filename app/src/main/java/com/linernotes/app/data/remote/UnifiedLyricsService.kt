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

    private val TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]""")

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        sourcePref: String = AiPreferences.LyricsSourcePreference.AUTO_FIRST.code,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        val pref = AiPreferences.LyricsSourcePreference.fromCode(sourcePref)

        val primaryResult = when (pref) {
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

        // 关键容灾兜底：若用户曾锁定了单一音源但该音源未收录或当前网络波动，自动平滑降级至全平台并发聚合检索，绝不返回空歌词
        if (primaryResult != null && primaryResult.originalLyrics.isNotBlank()) {
            return primaryResult
        }
        return if (pref != AiPreferences.LyricsSourcePreference.AUTO_FIRST) {
            fetchAutoAggregated(trackTitle, artistName, targetDurationMs)
        } else null
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
            // 若首轮并发竞速受冷启动握手或多源并发网络抢占未果，以 3500ms 紧凑超时窗口并发执行补抓，彻底杜绝 28s 串行长延时
            val directFallback = withTimeoutOrNull(3500L) {
                supervisorScope {
                    val fNetease = async { NetEaseLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs) }
                    val fQq = async { QQMusicLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs) }
                    val fKugou = async { KugouLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs) }
                    val fLrclib = async { LrclibLyricsService.fetchLyrics(trackTitle, artistName, targetDurationMs) }

                    val fbCandidates = listOf(fNetease, fQq, fKugou, fLrclib)
                    fbCandidates.mapNotNull { runCatching { it.await() }.getOrNull() }
                        .firstOrNull { it.originalLyrics.isNotBlank() }
                }
            }

            if (directFallback != null && directFallback.originalLyrics.isNotBlank()) {
                return@supervisorScope sanitizeResult(directFallback, null)
            }

            // 尝试轻量 Musixmatch 兜底
            return@supervisorScope withTimeoutOrNull(2500L) {
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

            // 2. 歌曲物理时长对齐验证 (首选候选曲目元数据真实总时长 res.durationMs，避免因片尾伴奏留白导致末句时间戳偏短而误扣分)
            val lyricLastTs = LyricSearchCleaner.extractLastTimestampMs(res.originalLyrics)
            val effectiveDuration = if (res.durationMs > 0L) res.durationMs else lyricLastTs
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

            // 如果胜出源自身缺少封面或缺少中文双语翻译，而同行候选源（如 QQ 音乐 / 网易云）具备，则智能跨源互补
            val resolvedCover = calibratedWinner.coverUrl ?: candidates.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
            val resolvedTrans = calibratedWinner.translatedLyrics?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull { !it.translatedLyrics.isNullOrBlank() }?.translatedLyrics
            val enrichedWinner = calibratedWinner.copy(
                coverUrl = resolvedCover ?: calibratedWinner.coverUrl,
                translatedLyrics = resolvedTrans ?: calibratedWinner.translatedLyrics,
                isBilingual = !resolvedTrans.isNullOrBlank() || calibratedWinner.isBilingual
            )

            val reference = lrclibRes?.takeIf { it !== best && it.originalLyrics.isNotBlank() }
                ?: candidates.firstOrNull { it !== best && !LyricSanitizer.hasCensorship(it.originalLyrics) && it.originalLyrics.isNotBlank() }
                ?: candidates.firstOrNull { it !== best && it.originalLyrics.isNotBlank() }
            sanitizeResult(enrichedWinner, reference)
        } else null
    }

    /**
     * 跨源时间轴交叉校验与 Spotify 原声时间基准校准：
     * 1. 修复重复副歌被 .toMap() 覆盖产生 200s 虚假漂移的缺陷，改用时间邻近窗口 (12s 内最近同名句) 提取有符号偏移量 (cTime - pTime)；
     * 2. 采用中位数偏移 (Median Offset) 与四分位距一致性检验：
     *    - 若主力双语源自身具备 LRC 时间轴，则对主力源的原文与译文**同步平移中位数偏移量**，绝不用外源不同行数的文本粗暴覆盖原文，从根源上保证双语 100% 同源对齐；
     *    - 仅当主力源完全缺失 LRC 时间戳 (primaryLacksLrc) 时，才借用基准源的高精度时间轴。
     */
    private fun calibrateWithSpotifyTiming(
        primary: OnlineLyricsResult,
        calibrator: OnlineLyricsResult
    ): OnlineLyricsResult {
        if (primary === calibrator) return primary
        if (calibrator.originalLyrics.isBlank() || !calibrator.originalLyrics.contains(TIMESTAMP_REGEX)) {
            return primary
        }
        if (!primary.isBilingual || primary.translatedLyrics.isNullOrBlank()) {
            return primary
        }

        if (primary.durationMs > 0L && calibrator.durationMs > 0L) {
            val durDiff = Math.abs(primary.durationMs - calibrator.durationMs)
            if (durDiff > 15_000L) return primary
        }

        val pLines = primary.originalLyrics.lines().map { LyricAligner.cleanLine(it) }.filter { it.isNotBlank() }
        val cLines = calibrator.originalLyrics.lines().map { LyricAligner.cleanLine(it) }.filter { it.isNotBlank() }
        if (pLines.isEmpty() || cLines.isEmpty()) return primary

        val pNorm = pLines.map { it.lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "") }.filter { it.isNotBlank() }
        val cNorm = cLines.map { it.lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "") }.filter { it.isNotBlank() }
        val commonCount = pNorm.count { p -> cNorm.contains(p) }
        val similarity = commonCount.toFloat() / pNorm.size.coerceAtLeast(1)
        if (similarity < 0.4f) return primary

        val primaryLacksLrc = !primary.originalLyrics.contains(TIMESTAMP_REGEX)
        if (primaryLacksLrc) {
            return primary.copy(
                originalLyrics = calibrator.originalLyrics,
                durationMs = if (primary.durationMs <= 0L) calibrator.durationMs else primary.durationMs
            )
        }

        // 提取带时间戳的歌词行（保留同名重复句的所有时间点列表，杜绝副歌被末次时间覆盖）
        val pWithTime = primary.originalLyrics.lines().mapNotNull { line ->
            val t = LyricAligner.extractTimestampMs(line) ?: return@mapNotNull null
            val norm = LyricAligner.cleanLine(line).lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "")
            if (norm.length >= 4) norm to t else null
        }
        val cTimeBuckets = mutableMapOf<String, MutableList<Long>>()
        for (line in calibrator.originalLyrics.lines()) {
            val t = LyricAligner.extractTimestampMs(line) ?: continue
            val norm = LyricAligner.cleanLine(line).lowercase().replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "")
            if (norm.length >= 4) {
                cTimeBuckets.getOrPut(norm) { mutableListOf() }.add(t)
            }
        }

        // 计算每句在 ±12s 邻近窗口内最近匹配句的有符号时间差 (delta = cTime - pTime)
        val signedDeltas = pWithTime.mapNotNull { (norm, pTime) ->
            val candidates = cTimeBuckets[norm] ?: return@mapNotNull null
            val closestCTime = candidates.minByOrNull { cTime -> kotlin.math.abs(cTime - pTime) } ?: return@mapNotNull null
            val diff = closestCTime - pTime
            if (kotlin.math.abs(diff) <= 12_000L) diff else null
        }.sorted()

        if (signedDeltas.size >= 4) {
            val medianDelta = signedDeltas[signedDeltas.size / 2]
            val q1 = signedDeltas[signedDeltas.size / 4]
            val q3 = signedDeltas[(signedDeltas.size * 3) / 4]
            val iqr = q3 - q1
            // 仅当中位数整体偏移超过 850ms 且全曲偏移方向高度一致 (四分位距 IQR <= 900ms) 时，
            // 对主力源自身的原文与译文同步平移该偏移量，既修正前奏延迟又 100% 保持原文与译文同源 1:1 对齐！
            if (kotlin.math.abs(medianDelta) in 850L..12_000L && iqr <= 900L) {
                return primary.copy(
                    originalLyrics = LyricAligner.shiftLrcTimestamps(primary.originalLyrics, medianDelta),
                    translatedLyrics = primary.translatedLyrics?.let { LyricAligner.shiftLrcTimestamps(it, medianDelta) }
                )
            }
        }
        return primary
    }

    private fun sanitizeResult(result: OnlineLyricsResult, refResult: OnlineLyricsResult?): OnlineLyricsResult {
        val unescapedOrig = com.linernotes.app.core.util.HtmlUtils.unescapeHtml(result.originalLyrics)
        val unescapedTrans = result.translatedLyrics?.let { com.linernotes.app.core.util.HtmlUtils.unescapeHtml(it) }
        val unescapedTitle = com.linernotes.app.core.util.HtmlUtils.unescapeHtml(result.title)

        val hasOrigCensor = LyricSanitizer.hasCensorship(unescapedOrig)
        val hasTransCensor = LyricSanitizer.hasCensorship(unescapedTrans)
        val hasTitleCensor = LyricSanitizer.hasCensorship(unescapedTitle)

        val cleanLyrics = if (hasOrigCensor) {
            LyricSanitizer.decensorLyrics(unescapedOrig, refResult?.originalLyrics, unescapedTrans)
        } else unescapedOrig
        val cleanChinese = if (hasTransCensor) {
            LyricSanitizer.decensorChineseLyrics(unescapedTrans, cleanLyrics)
        } else unescapedTrans
        val cleanTitle = if (hasTitleCensor) {
            LyricSanitizer.decensorTitle(unescapedTitle, refResult?.title)
        } else unescapedTitle
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
        // 3. 歌曲物理时长对齐验证 (首选曲目真实元数据总时长 res.durationMs，误差 <= 8s)
        if (targetDurationMs > 0L) {
            val lyricLastTs = LyricSearchCleaner.extractLastTimestampMs(res.originalLyrics)
            val effectiveDuration = if (res.durationMs > 0L) res.durationMs else lyricLastTs
            if (effectiveDuration > 0L) {
                val diffSec = Math.abs(targetDurationMs - effectiveDuration) / 1000L
                if (diffSec > 8L) return false
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
