package com.linernotes.app.data.remote

import android.util.Base64
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object KugouLyricsService {

    private const val SEARCH_API = "https://songsearch.kugou.com/song_search_v2"
    private const val CANDIDATE_API = "https://krcs.kugou.com/search"
    private const val DOWNLOAD_API = "https://krcs.kugou.com/download"

    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        val queries = LyricSearchCleaner.buildSearchQueries(trackTitle, artistName)
        val cleanArtist = LyricSearchCleaner.cleanArtist(artistName).lowercase()

        for (query in queries) {
            val result = searchAndFetch(query, cleanArtist, trackTitle, artistName)
            if (result != null && result.originalLyrics.isNotBlank()) {
                return@withContext result
            }
        }
        null
    }

    private fun searchAndFetch(
        query: String,
        cleanArtist: String,
        fallbackTitle: String,
        fallbackArtist: String
    ): OnlineLyricsResult? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$SEARCH_API?keyword=$encodedQuery&page=1&pagesize=5&platform=WebFilter"
            val searchJson = LinerNotesHttpClient.get(searchUrl, HEADERS) ?: return null

            val searchRoot = JSONObject(searchJson)
            val dataObj = searchRoot.optJSONObject("data") ?: return null
            val list = dataObj.optJSONArray("lists") ?: return null
            if (list.length() == 0) return null

            var targetSong: JSONObject? = null

            if (cleanArtist.isNotBlank()) {
                for (i in 0 until list.length()) {
                    val s = list.optJSONObject(i) ?: continue
                    val singer = s.optString("SingerName", "").lowercase()
                    if (singer.isNotBlank() && (singer.contains(cleanArtist) || cleanArtist.contains(singer))) {
                        targetSong = s
                        break
                    }
                }
            }

            if (targetSong == null) {
                targetSong = list.optJSONObject(0) ?: return null
            }

            val hash = targetSong.optString("FileHash", "")
            val duration = targetSong.optLong("Duration", 0L) * 1000L
            val matchedTitle = targetSong.optString("SongName", fallbackTitle)
            val matchedArtist = targetSong.optString("SingerName", fallbackArtist)

            if (hash.isBlank()) return null

            // 检索歌词候选集
            val encTitle = URLEncoder.encode(LyricSearchCleaner.cleanTrackTitle(fallbackTitle), "UTF-8")
            val candUrl = "$CANDIDATE_API?ver=1&man=yes&client=mobi&keyword=$encTitle&duration=$duration&hash=$hash"
            val candJson = LinerNotesHttpClient.get(candUrl, HEADERS) ?: return null
            val candRoot = JSONObject(candJson)
            val candidates = candRoot.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val cand = candidates.optJSONObject(0) ?: return null
            val id = cand.optString("id", "")
            val accesskey = cand.optString("accesskey", "")
            if (id.isBlank() || accesskey.isBlank()) return null

            // 下载歌词
            val downUrl = "$DOWNLOAD_API?ver=1&client=mobi&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8"
            val downJson = LinerNotesHttpClient.get(downUrl, HEADERS) ?: return null
            val downRoot = JSONObject(downJson)
            val b64Content = downRoot.optString("content", "")
            if (b64Content.isBlank()) return null

            val rawLrc = try {
                String(Base64.decode(b64Content, Base64.DEFAULT), Charsets.UTF_8).trim()
            } catch (e: Exception) {
                return null
            }

            if (rawLrc.isBlank()) return null

            val alignedPair = LyricAligner.alignLrcTimestamps(rawLrc, null)

            OnlineLyricsResult(
                songId = 0L,
                title = matchedTitle,
                artist = matchedArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = null,
                isBilingual = false
            )
        } catch (e: Exception) {
            null
        }
    }
}
