package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder

object LrclibLyricsService {

    private const val SEARCH_API = "https://lrclib.net/api/search"

    private val HEADERS = mapOf(
        "User-Agent" to "LinerNotes/1.0 (Android; https://github.com/MarcoWong06-12/LinerNotes)"
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
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_API?q=$encQuery"
            val jsonStr = LinerNotesHttpClient.get(url, HEADERS) ?: return null
            val array = JSONArray(jsonStr)
            if (array.length() == 0) return null

            var bestItem: org.json.JSONObject? = null

            // 1. 优先挑选包含匹配歌手且含有同步歌词的项
            if (cleanArtist.isNotBlank()) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val art = item.optString("artistName", "").lowercase()
                    val hasSynced = item.optString("syncedLyrics", "").isNotBlank()
                    if (art.isNotBlank() && (art.contains(cleanArtist) || cleanArtist.contains(art))) {
                        if (hasSynced) {
                            bestItem = item
                            break
                        } else if (bestItem == null) {
                            bestItem = item
                        }
                    }
                }
            }

            // 2. 其次挑选包含同步歌词的第一项
            if (bestItem == null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.optString("syncedLyrics", "").isNotBlank()) {
                        bestItem = item
                        break
                    }
                }
            }

            // 3. 最后保底选用第 1 项
            if (bestItem == null) {
                bestItem = array.optJSONObject(0) ?: return null
            }

            val synced = bestItem.optString("syncedLyrics", "")
            val plain = bestItem.optString("plainLyrics", "")

            val lyrics = when {
                synced.isNotBlank() -> LyricAligner.alignLrcTimestamps(synced, null).first
                plain.isNotBlank() -> plain.trim()
                else -> return null
            }

            val trackName = bestItem.optString("trackName", fallbackTitle)
            val artist = bestItem.optString("artistName", fallbackArtist)

            OnlineLyricsResult(
                songId = bestItem.optLong("id", 0L),
                title = trackName,
                artist = artist,
                originalLyrics = lyrics,
                translatedLyrics = null,
                isBilingual = false
            )
        } catch (e: Exception) {
            null
        }
    }
}
