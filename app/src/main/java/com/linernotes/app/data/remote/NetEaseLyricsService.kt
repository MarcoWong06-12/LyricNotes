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
    private const val WEB_SEARCH_API = "https://music.163.com/api/search/get/web"
    private const val LYRIC_API = "https://music.163.com/api/song/lyric"

    private val HEADERS = mapOf(
        "Referer" to "https://music.163.com/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    suspend fun fetchLyricById(
        songId: Long,
        fallbackTitle: String = "",
        fallbackArtist: String = ""
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        if (songId <= 0L) return@withContext null
        val lyricUrl = "$LYRIC_API?id=$songId&lv=1&kv=1&tv=1"
        val lyricJson = LinerNotesHttpClient.getAsync(lyricUrl, HEADERS) ?: return@withContext null
        try {
            val lyricRoot = JSONObject(lyricJson)
            val lrcObj = lyricRoot.optJSONObject("lrc")
            val origLrc = lrcObj?.optString("lyric", "")?.trim() ?: ""
            if (origLrc.isBlank()) return@withContext null

            val tlyricObj = lyricRoot.optJSONObject("tlyric")
            val transLrc = tlyricObj?.optString("lyric", "")?.trim() ?: ""

            val alignedPair = LyricAligner.alignLrcTimestamps(origLrc, transLrc)

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
        val matchedDuration = s.optLong("dt", 0L)
        val matchedCover = s.optJSONObject("al")?.optString("picUrl")?.takeIf { it.isNotBlank() }

        val lyricUrl = "$LYRIC_API?id=$songId&lv=1&kv=1&tv=1"
        val lyricJson = LinerNotesHttpClient.getAsync(lyricUrl, HEADERS) ?: return null

        return try {
            val lyricRoot = JSONObject(lyricJson)
            val lrcObj = lyricRoot.optJSONObject("lrc")
            val origLrc = lrcObj?.optString("lyric", "")?.trim() ?: ""
            if (origLrc.isBlank()) return null

            val tlyricObj = lyricRoot.optJSONObject("tlyric")
            val transLrc = tlyricObj?.optString("lyric", "")?.trim() ?: ""

            val alignedPair = LyricAligner.alignLrcTimestamps(origLrc, transLrc)

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
            val candDuration = s.optLong("dt", 0L)

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
                    val lyricUrl = "$LYRIC_API?id=${item.sourceId}&lv=1&kv=1&tv=1"
                    val json = LinerNotesHttpClient.getAsync(lyricUrl, HEADERS)
                    if (json != null) {
                        try {
                            val root = JSONObject(json)
                            val tlyric = root.optJSONObject("tlyric")?.optString("lyric", "")?.trim() ?: ""
                            if (tlyric.isNotBlank()) {
                                item.copy(hasTranslation = true)
                            } else item
                        } catch (e: Exception) { item }
                    } else item
                }
            }.awaitAll()
        } + list.drop(6)
    }

    private suspend fun searchSongs(query: String, limit: Int = 5): JSONArray? {
        // 首选 CloudSearch POST
        try {
            val postParams = mapOf(
                "s" to query,
                "type" to "1",
                "offset" to "0",
                "limit" to limit.toString()
            )
            val jsonStr = LinerNotesHttpClient.postFormAsync(CLOUD_SEARCH_API, postParams, HEADERS)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val result = root.optJSONObject("result")
                val songs = result?.optJSONArray("songs")
                if (songs != null && songs.length() > 0) {
                    return songs
                }
            }
        } catch (e: Exception) {
            // 继续回退
        }

        // 回退 WebSearch GET
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
