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
        return try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_API?q=$encQuery"
            val jsonStr = LinerNotesHttpClient.getAsync(url, HEADERS) ?: return null
            val array = JSONArray(jsonStr)
            if (array.length() == 0) return null

            var bestItem: org.json.JSONObject? = null
            var bestScore = -100

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val candTitle = item.optString("trackName", "")
                val candArtist = item.optString("artistName", "")
                val candDuration = (item.optDouble("duration", 0.0) * 1000).toLong()
                val hasSynced = item.optString("syncedLyrics", "").isNotBlank()

                var score = LyricSearchCleaner.scoreCandidateMatch(
                    candidateTitle = candTitle,
                    candidateArtist = candArtist,
                    targetTitle = targetTitle,
                    targetArtist = targetArtist,
                    candidateDurationMs = candDuration,
                    targetDurationMs = targetDurationMs
                )

                if (hasSynced) score += 30

                if (score > bestScore && score >= 50) {
                    bestScore = score
                    bestItem = item
                }
            }

            if (bestItem == null) return null

            val synced = bestItem.optString("syncedLyrics", "")
            val plain = bestItem.optString("plainLyrics", "")

            val lyrics = when {
                synced.isNotBlank() -> LyricAligner.alignLrcTimestamps(synced, null).first
                plain.isNotBlank() -> plain.trim()
                else -> return null
            }

            val trackName = bestItem.optString("trackName", targetTitle)
            val artist = bestItem.optString("artistName", targetArtist)
            val duration = (bestItem.optDouble("duration", 0.0) * 1000).toLong()

            OnlineLyricsResult(
                songId = bestItem.optLong("id", 0L),
                title = trackName,
                artist = artist,
                originalLyrics = lyrics,
                translatedLyrics = null,
                isBilingual = false,
                coverUrl = null,
                durationMs = duration
            )
        } catch (e: Exception) {
            null
        }
    }
}
