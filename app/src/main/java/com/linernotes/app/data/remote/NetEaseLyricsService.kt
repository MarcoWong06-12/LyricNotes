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
        val lyricJson = LinerNotesHttpClient.get(lyricUrl, HEADERS) ?: return@withContext null
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
        val songs = searchSongs(query) ?: return null
        if (songs.length() == 0) return null

        var bestSong: JSONObject? = null

        // 1. 若提供了歌手名，优先挑选包含匹配歌手的条目
        if (cleanArtist.isNotBlank()) {
            for (i in 0 until songs.length()) {
                val s = songs.optJSONObject(i) ?: continue
                val artists = s.optJSONArray("ar") ?: s.optJSONArray("artists")
                if (artists != null) {
                    for (j in 0 until artists.length()) {
                        val aName = artists.optJSONObject(j)?.optString("name", "")?.lowercase() ?: ""
                        if (aName.isNotBlank() && (aName.contains(cleanArtist) || cleanArtist.contains(aName))) {
                            bestSong = s
                            break
                        }
                    }
                }
                if (bestSong != null) break
            }
        }

        // 2. 无匹配或未指定歌手时，使用检索权重最高的第 1 个结果
        if (bestSong == null) {
            bestSong = songs.optJSONObject(0) ?: return null
        }

        val songId = bestSong.optLong("id", 0L)
        if (songId <= 0L) return null

        val matchedTitle = bestSong.optString("name", fallbackTitle)
        val matchedArtists = bestSong.optJSONArray("ar") ?: bestSong.optJSONArray("artists")
        val matchedArtist = matchedArtists?.optJSONObject(0)?.optString("name", fallbackArtist) ?: fallbackArtist

        // 获取原版与翻译歌词
        val lyricUrl = "$LYRIC_API?id=$songId&lv=1&kv=1&tv=1"
        val lyricJson = LinerNotesHttpClient.get(lyricUrl, HEADERS) ?: return null

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
                isBilingual = alignedPair.second.isNotBlank()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun searchSongs(query: String): JSONArray? {
        // 首选 CloudSearch POST
        try {
            val postParams = mapOf(
                "s" to query,
                "type" to "1",
                "offset" to "0",
                "limit" to "5"
            )
            val jsonStr = LinerNotesHttpClient.postForm(CLOUD_SEARCH_API, postParams, HEADERS)
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
            val jsonStr = LinerNotesHttpClient.get(getUrl, HEADERS) ?: return null
            val root = JSONObject(jsonStr)
            root.optJSONObject("result")?.optJSONArray("songs")
        } catch (e: Exception) {
            null
        }
    }
}
