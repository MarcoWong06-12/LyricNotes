package com.linernotes.app.data.remote

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
        // 若网易云或 QQ 音乐等官方主力源已返回高置信度双语时间轴歌词，直接极速返回，无需等待慢速源超时
        val fastDeadline = System.currentTimeMillis() + 1800L
        while (System.currentTimeMillis() < fastDeadline && isActive) {
            if (neteaseDeferred.isCompleted) {
                val res = runCatching { neteaseDeferred.await() }.getOrNull()
                if (res != null && isFastPathQualified(res, targetDurationMs, keywords)) {
                    qqDeferred.cancel()
                    kugouDeferred.cancel()
                    lrclibDeferred.cancel()
                    return@supervisorScope sanitizeResult(res, null)
                }
            }
            if (qqDeferred.isCompleted) {
                val res = runCatching { qqDeferred.await() }.getOrNull()
                if (res != null && isFastPathQualified(res, targetDurationMs, keywords)) {
                    neteaseDeferred.cancel()
                    kugouDeferred.cancel()
                    lrclibDeferred.cancel()
                    return@supervisorScope sanitizeResult(res, null)
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
            // 如果胜出源自身缺少封面，但同行候选源具备高清封面，智能补齐封面 URL
            val resolvedCover = best.coverUrl ?: candidates.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
            val enrichedWinner = if (resolvedCover != null && best.coverUrl == null) {
                best.copy(coverUrl = resolvedCover)
            } else best

            val reference = candidates.firstOrNull { it !== best && it.originalLyrics.isNotBlank() }
            sanitizeResult(enrichedWinner, reference)
        } else null
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
        // 3. 歌曲物理时长对齐验证 (误差 <= 8s)
        if (targetDurationMs > 0L) {
            val lyricLastTs = LyricSearchCleaner.extractLastTimestampMs(res.originalLyrics)
            val effectiveDuration = if (lyricLastTs > 0L) lyricLastTs else res.durationMs
            if (effectiveDuration > 0L) {
                val diffSec = Math.abs(targetDurationMs - effectiveDuration) / 1000L
                if (diffSec > 8L) return false
            }
        }
        // 4. 标题实质关键词核验（如果有提取到显著关键词，标题或歌词至少部分命中）
        if (keywords.isNotEmpty()) {
            val relevance = LyricSearchCleaner.calculateTitleKeywordRelevance(res.originalLyrics, keywords)
            val titleMatches = keywords.any { kw -> res.title.contains(kw, ignoreCase = true) }
            if (relevance <= 0f && !titleMatches) return false
        }
        return true
    }
}
