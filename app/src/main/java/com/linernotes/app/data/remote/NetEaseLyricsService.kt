package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object NetEaseLyricsService {

    private const val CLOUD_SEARCH_API = "https://music.163.com/api/cloudsearch/pc"
    private const val INTERFACE_SEARCH_API = "https://interface.music.163.com/api/cloudsearch/pc"
    private const val WEB_SEARCH_API = "https://music.163.com/api/search/get/web"
    private const val LYRIC_API = "https://music.163.com/api/song/lyric"
    private const val INTERFACE_LYRIC_API = "https://interface.music.163.com/api/song/lyric"

    private suspend fun fetchLyricRootJson(songId: Long, queryParams: String): JSONObject? {
        val urls = listOf(
            "$LYRIC_API?id=$songId&$queryParams",
            "$INTERFACE_LYRIC_API?id=$songId&$queryParams"
        )
        for (url in urls) {
            val raw = LinerNotesHttpClient.getAsync(url, HEADERS) ?: continue
            val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val hasPayload = parsed.optJSONObject("lrc") != null ||
                parsed.optJSONObject("yrc") != null ||
                parsed.optJSONObject("tlyric") != null ||
                parsed.optJSONObject("ytlrc") != null
            if (hasPayload) return parsed
        }
        return null
    }

    private val HEADERS = mapOf(
        "Referer" to "https://music.163.com/",
        "Cookie" to "os=pc; appver=2.9.7; Miles=1",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    private val YRC_WORD_LINE_REGEX = Regex("""^\[\d+,\d+\].*\(\d+,\d+(?:,\d+)?\)""")
    private val LRC_TIMED_LINE_REGEX = Regex("""^\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]""")
    private val INLINE_TAG_STRIP_REGEX = Regex("""\[\d+,\d+\]|\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]|\(\d+,\d+(?:,\d+)?\)""")

    private fun isSafePlainText(text: String): Boolean {
        if (text.isBlank()) return false
        return text.none { ch ->
            ch == '\u0000' || (ch.code in 0x01..0x08) || ch.code == 0x0B || ch.code == 0x0C || (ch.code in 0x0E..0x1F)
        }
    }

    internal fun isValidWordTimedYrc(yrc: String?): Boolean {
        if (yrc.isNullOrBlank() || !isSafePlainText(yrc)) return false
        return yrc.lines().any { line ->
            val trimmed = line.trim()
            YRC_WORD_LINE_REGEX.containsMatchIn(trimmed) &&
                trimmed.replace(INLINE_TAG_STRIP_REGEX, "").isNotBlank()
        }
    }

    internal fun selectPreferredNetEaseLyricPayload(
        origLrc: String,
        transLrc: String,
        yrcLrc: String,
        ytLrc: String
    ): Pair<String, String>? {
        val safeOrig = origLrc.takeIf { isSafePlainText(it) }.orEmpty()
        val safeTrans = transLrc.takeIf { isSafePlainText(it) }.orEmpty()
        val safeYrc = yrcLrc.takeIf { isValidWordTimedYrc(it) }.orEmpty()
        val safeYt = ytLrc.takeIf { isSafePlainText(it) }.orEmpty()

        if (safeYrc.isNotBlank()) {
            val yrcLineCount = safeYrc.lines().count { line ->
                val trimmed = line.trim()
                YRC_WORD_LINE_REGEX.containsMatchIn(trimmed) &&
                    trimmed.replace(INLINE_TAG_STRIP_REGEX, "").isNotBlank()
            }
            val lrcLineCount = safeOrig.lines().count { line ->
                val trimmed = line.trim()
                LRC_TIMED_LINE_REGEX.containsMatchIn(trimmed) &&
                    trimmed.replace(INLINE_TAG_STRIP_REGEX, "").isNotBlank()
            }
            if (yrcLineCount >= 1 && (lrcLineCount == 0 || yrcLineCount * 2 >= lrcLineCount)) {
                return safeYrc to safeYt.ifBlank { safeTrans }
            }
        }

        if (safeOrig.isNotBlank()) {
            return safeOrig to safeTrans
        }
        return null
    }

    internal fun alignPreferredNetEaseLyrics(
        origLrc: String,
        transLrc: String,
        yrcLrc: String,
        ytLrc: String
    ): Pair<String, String>? {
        val selected = selectPreferredNetEaseLyricPayload(origLrc, transLrc, yrcLrc, ytLrc) ?: return null
        val aligned = LyricAligner.alignLrcTimestamps(selected.first, selected.second)
        // 若选中了 YRC 但 tlyric 与 YRC 时间轴不兼容导致官方译文丢失、而原 LRC 可成功对齐官方译文，优先保真原双语对齐或保持 YRC
        if (selected.first == yrcLrc && isValidWordTimedYrc(selected.first) && selected.second.isNotBlank() && aligned.second.isBlank() && origLrc.isNotBlank()) {
            val lrcAligned = LyricAligner.alignLrcTimestamps(origLrc, transLrc)
            if (lrcAligned.second.isNotBlank()) {
                return lrcAligned
            }
        }
        // 当使用 YRC + ytlrc 且同响应还存在 tlyric 时，用 tlyric 补全 ytlrc 常因敏感词/漏翻而缺席的个别行（如 SZA 等曲目）
        if (selected.first == yrcLrc && ytLrc.isNotBlank() && transLrc.isNotBlank() && aligned.second.isNotBlank()) {
            val mergedTrans = mergeMissingLinesWithFallbackTranslation(
                alignedOrig = aligned.first,
                primaryAlignedTrans = aligned.second,
                fallbackTransLrc = transLrc
            )
            if (mergedTrans.isNotBlank()) {
                return aligned.first to mergedTrans
            }
        }
        return aligned
    }

    internal fun mergeMissingLinesWithFallbackTranslation(
        alignedOrig: String,
        primaryAlignedTrans: String,
        fallbackTransLrc: String
    ): String {
        val origLines = alignedOrig.lines()
        val primaryLines = primaryAlignedTrans.lines()
        val hasMissingLine = origLines.indices.any { i ->
            LyricAligner.cleanLine(origLines[i]).isNotBlank() &&
                LyricAligner.cleanLine(primaryLines.getOrNull(i).orEmpty()).isBlank()
        }
        if (!hasMissingLine) return primaryAlignedTrans

        val fallbackAligned = LyricAligner.alignLrcTimestamps(alignedOrig, fallbackTransLrc)
        val fallbackLines = fallbackAligned.second.lines()
        if (fallbackLines.all { it.isBlank() }) return primaryAlignedTrans

        return origLines.indices.joinToString("\n") { i ->
            val p = primaryLines.getOrNull(i).orEmpty()
            if (LyricAligner.cleanLine(p).isNotBlank()) {
                p
            } else {
                val f = fallbackLines.getOrNull(i).orEmpty()
                if (LyricAligner.cleanLine(f).isNotBlank()) f else p
            }
        }.trimEnd()
    }

    suspend fun fetchLyricById(
        songId: Long,
        fallbackTitle: String = "",
        fallbackArtist: String = ""
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        if (songId <= 0L) return@withContext null
        val lyricRoot = fetchLyricRootJson(songId, "lv=1&kv=1&tv=1&yv=1&ytv=1") ?: return@withContext null
        try {
            val origLrc = lyricRoot.optJSONObject("lrc")?.optString("lyric", "")?.trim() ?: ""
            val transLrc = lyricRoot.optJSONObject("tlyric")?.optString("lyric", "")?.trim() ?: ""
            val yrcLrc = lyricRoot.optJSONObject("yrc")?.optString("lyric", "")?.trim() ?: ""
            val ytLrc = lyricRoot.optJSONObject("ytlrc")?.optString("lyric", "")?.trim() ?: ""

            val alignedPair = alignPreferredNetEaseLyrics(origLrc, transLrc, yrcLrc, ytLrc)
                ?: return@withContext null
            if (alignedPair.first.isBlank()) return@withContext null

            OnlineLyricsResult(
                songId = songId,
                title = fallbackTitle,
                artist = fallbackArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = alignedPair.second.ifBlank { null },
                isBilingual = alignedPair.second.isNotBlank()
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        val cleanTitle = LyricSearchCleaner.cleanTrackTitle(trackTitle).ifBlank { trackTitle }
        val queries = LyricSearchCleaner.buildSearchQueries(trackTitle, artistName)

        for (query in queries) {
            val result = searchAndFetch(query, cleanTitle, artistName, targetDurationMs)
            if (result != null && result.originalLyrics.isNotBlank()) {
                return@withContext result
            }
        }
        null
    }

    private data class ScoredCandidate(
        val songJson: JSONObject,
        val score: Int
    )

    private suspend fun fetchSongLyricsById(
        s: JSONObject,
        score: Int,
        targetTitle: String,
        targetArtist: String
    ): OnlineLyricsResult? {
        val songId = s.optLong("id", 0L)
        if (songId <= 0L) return null

        val matchedTitle = s.optString("name", targetTitle)
        val matchedArtists = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        val matchedArtist = matchedArtists?.optJSONObject(0)?.optString("name", targetArtist) ?: targetArtist
        val matchedDuration = s.optLong("dt", 0L).takeIf { it > 0L } ?: s.optLong("duration", 0L)
        val matchedCover = (s.optJSONObject("al") ?: s.optJSONObject("album"))
            ?.optString("picUrl")
            ?.takeIf { it.isNotBlank() }

        val lyricRoot = fetchLyricRootJson(songId, "lv=1&kv=1&tv=1&yv=1&ytv=1") ?: return null

        return try {
            val origLrc = lyricRoot.optJSONObject("lrc")?.optString("lyric", "")?.trim() ?: ""
            val transLrc = lyricRoot.optJSONObject("tlyric")?.optString("lyric", "")?.trim() ?: ""
            val yrcLrc = lyricRoot.optJSONObject("yrc")?.optString("lyric", "")?.trim() ?: ""
            val ytLrc = lyricRoot.optJSONObject("ytlrc")?.optString("lyric", "")?.trim() ?: ""

            val alignedPair = alignPreferredNetEaseLyrics(origLrc, transLrc, yrcLrc, ytLrc) ?: return null
            if (alignedPair.first.isBlank()) return null

            OnlineLyricsResult(
                songId = songId,
                title = matchedTitle,
                artist = matchedArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = alignedPair.second.ifBlank { null },
                isBilingual = alignedPair.second.isNotBlank(),
                coverUrl = matchedCover,
                durationMs = matchedDuration
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun searchAndFetch(
        query: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        val songs = searchSongs(query) ?: return null
        if (songs.length() == 0) return null

        val scoredList = mutableListOf<ScoredCandidate>()

        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val candTitle = s.optString("name", "")
            val artists = s.optJSONArray("ar") ?: s.optJSONArray("artists")
            val candArtist = if (artists != null) {
                (0 until artists.length()).mapNotNull { idx -> artists.optJSONObject(idx)?.optString("name") }.joinToString(", ")
            } else ""
            val candDuration = s.optLong("dt", 0L).takeIf { it > 0L } ?: s.optLong("duration", 0L)

            val score = LyricSearchCleaner.scoreCandidateMatch(
                candidateTitle = candTitle,
                candidateArtist = candArtist,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                candidateDurationMs = candDuration,
                targetDurationMs = targetDurationMs
            )

            if (score >= 45) {
                scoredList.add(ScoredCandidate(s, score))
            }
        }

        if (scoredList.isEmpty()) return null
        scoredList.sortByDescending { it.score }

        // 多候选版本深度嗅探（并发探测前 4 个高分版本，优先捕获官方/社区人工双语精翻）
        val topCandidates = scoredList.take(4)
        val fetchedResults = supervisorScope {
            topCandidates.map { cand ->
                async {
                    fetchSongLyricsById(cand.songJson, cand.score, targetTitle, targetArtist)?.let { res ->
                        cand to res
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (fetchedResults.isEmpty()) return null

        // 1. 优先选择拥有有效人工双语翻译 (isBilingual && translatedLyrics 非空) 的高分候选
        val bestBilingual = fetchedResults
            .filter { (_, res) -> res.isBilingual && !res.translatedLyrics.isNullOrBlank() }
            .maxByOrNull { (cand, _) -> cand.score }

        if (bestBilingual != null) {
            return bestBilingual.second
        }

        // 2. 若所有版本均无双语翻译，则回退到匹配得分最高的版本
        return fetchedResults.maxByOrNull { (cand, _) -> cand.score }?.second
    }

    suspend fun searchRawCandidates(query: String, limit: Int = 8): List<com.linernotes.app.domain.model.LyricCandidateItem> = withContext(Dispatchers.IO) {
        val songs = searchSongs(query, limit) ?: return@withContext emptyList()
        val list = mutableListOf<com.linernotes.app.domain.model.LyricCandidateItem>()
        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val id = s.optLong("id", 0L)
            if (id <= 0L) continue
            val title = s.optString("name", "")
            val artists = s.optJSONArray("ar") ?: s.optJSONArray("artists")
            val artist = if (artists != null) {
                (0 until artists.length()).mapNotNull { idx -> artists.optJSONObject(idx)?.optString("name") }.joinToString(", ")
            } else ""
            val alObj = s.optJSONObject("al") ?: s.optJSONObject("album")
            val album = alObj?.optString("name", "") ?: ""
            val cover = alObj?.optString("picUrl")?.takeIf { it.isNotBlank() }
            val durationMs = s.optLong("dt", 0L).takeIf { it > 0 } ?: s.optLong("duration", 0L)
            list.add(
                com.linernotes.app.domain.model.LyricCandidateItem(
                    source = com.linernotes.app.domain.model.LyricSource.NETEASE,
                    sourceId = id.toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    coverUrl = cover
                )
            )
        }
        // 并发探测前 6 个候选是否具备官方双语翻译
        supervisorScope {
            list.take(6).map { item ->
                async {
                    val songId = item.sourceId.toLongOrNull() ?: return@async item
                    val root = fetchLyricRootJson(songId, "lv=1&kv=1&tv=1") ?: return@async item
                    try {
                        val tlyric = root.optJSONObject("tlyric")?.optString("lyric", "")?.trim() ?: ""
                        if (tlyric.isNotBlank()) {
                            item.copy(hasTranslation = true)
                        } else item
                    } catch (e: Exception) { item }
                }
            }.awaitAll()
        } + list.drop(6)
    }

    private suspend fun searchSongs(query: String, limit: Int = 5): JSONArray? {
        val postParams = mapOf(
            "s" to query,
            "type" to "1",
            "offset" to "0",
            "limit" to limit.toString()
        )
        // 1. 首选 CloudSearch POST
        try {
            val jsonStr = LinerNotesHttpClient.postFormAsync(CLOUD_SEARCH_API, postParams, HEADERS)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val songs = root.optJSONObject("result")?.optJSONArray("songs")
                if (songs != null && songs.length() > 0) {
                    return songs
                }
            }
        } catch (e: Exception) {
            // 继续回退
        }

        // 2. 次选 Interface 独立网关 POST (5G 移动网络防 -460 拦截)
        try {
            val jsonStr = LinerNotesHttpClient.postFormAsync(INTERFACE_SEARCH_API, postParams, HEADERS)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val songs = root.optJSONObject("result")?.optJSONArray("songs")
                if (songs != null && songs.length() > 0) {
                    return songs
                }
            }
        } catch (e: Exception) {
            // 继续回退
        }

        // 3. 回退 WebSearch GET
        return try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val getUrl = "$WEB_SEARCH_API?s=$encQuery&type=1&limit=$limit"
            val jsonStr = LinerNotesHttpClient.getAsync(getUrl, HEADERS) ?: return null
            val root = JSONObject(jsonStr)
            root.optJSONObject("result")?.optJSONArray("songs")
        } catch (e: Exception) {
            null
        }
    }
}
