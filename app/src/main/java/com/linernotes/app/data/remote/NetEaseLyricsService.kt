package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
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
        val queries = LyricSearchCleaner.buildSearchQueries(trackTitle, artistName)

        for (query in queries) {
            val result = searchAndFetch(query, trackTitle, artistName, targetDurationMs)
            if (result != null && result.originalLyrics.isNotBlank()) {
                return@withContext result
            }
        }
        null
    }

    private suspend fun searchAndFetch(
        query: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        val songs = searchSongs(query) ?: return null
        if (songs.length() == 0) return null

        var bestSong: JSONObject? = null
        var bestScore = -100

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

            if (score > bestScore && score >= 50) {
                bestScore = score
                bestSong = s
            }
        }

        if (bestSong == null) return null

        val songId = bestSong.optLong("id", 0L)
        if (songId <= 0L) return null

        val matchedTitle = bestSong.optString("name", targetTitle)
        val matchedArtists = bestSong.optJSONArray("ar") ?: bestSong.optJSONArray("artists")
        val matchedArtist = matchedArtists?.optJSONObject(0)?.optString("name", targetArtist) ?: targetArtist
        val matchedDuration = bestSong.optLong("dt", 0L)
        val matchedCover = bestSong.optJSONObject("al")?.optString("picUrl")?.takeIf { it.isNotBlank() }

        // 获取原版与翻译歌词
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

    private suspend fun searchSongs(query: String): JSONArray? {
        // 首选 CloudSearch POST
        try {
            val postParams = mapOf(
                "s" to query,
                "type" to "1",
                "offset" to "0",
                "limit" to "5"
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
            val getUrl = "$WEB_SEARCH_API?s=$encQuery&type=1&limit=5"
            val jsonStr = LinerNotesHttpClient.getAsync(getUrl, HEADERS) ?: return null
            val root = JSONObject(jsonStr)
            root.optJSONObject("result")?.optJSONArray("songs")
        } catch (e: Exception) {
            null
        }
    }
}
