package com.linernotes.app.data.remote

import android.util.Base64
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object QQMusicLyricsService {

    private const val SEARCH_API = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val LYRIC_API = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"

    private val SEARCH_HEADERS = mapOf(
        "Referer" to "https://y.qq.com/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    private val LYRIC_HEADERS = mapOf(
        "Referer" to "https://y.qq.com/portal/player.html",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
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

    private fun searchAndFetch(
        query: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$SEARCH_API?p=1&n=5&w=$encodedQuery&format=json"
            val searchJson = LinerNotesHttpClient.get(searchUrl, SEARCH_HEADERS) ?: return null

            val searchRoot = JSONObject(searchJson)
            val dataObj = searchRoot.optJSONObject("data") ?: return null
            val songObj = dataObj.optJSONObject("song") ?: return null
            val songList = songObj.optJSONArray("list") ?: return null
            if (songList.length() == 0) return null

            var targetSong: JSONObject? = null
            var bestScore = -100

            for (i in 0 until songList.length()) {
                val s = songList.optJSONObject(i) ?: continue
                val candTitle = s.optString("songname", "")
                val singers = s.optJSONArray("singer")
                val candArtist = if (singers != null) {
                    (0 until singers.length()).mapNotNull { idx -> singers.optJSONObject(idx)?.optString("name") }.joinToString(", ")
                } else ""
                val candDuration = s.optLong("interval", 0L) * 1000L

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
                    targetSong = s
                }
            }

            if (targetSong == null) return null

            val songmid = targetSong.optString("songmid", "")
            if (songmid.isBlank()) return null

            val matchedTitle = targetSong.optString("songname", targetTitle)
            val matchedArtist = targetSong.optJSONArray("singer")?.optJSONObject(0)?.optString("name", targetArtist) ?: targetArtist
            val albummid = targetSong.optString("albummid", "")
            val matchedCover = if (albummid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg" else null
            val matchedDuration = targetSong.optLong("interval", 0L) * 1000L

            val lyricUrl = "$LYRIC_API?songmid=$songmid&format=json&nobase64=1"
            val lyricJson = LinerNotesHttpClient.get(lyricUrl, LYRIC_HEADERS) ?: return null
            val lyricRoot = JSONObject(lyricJson)

            var rawLrc = lyricRoot.optString("lyric", "")
            var rawTrans = lyricRoot.optString("trans", "")

            if (isBase64(rawLrc)) {
                rawLrc = decodeBase64(rawLrc)
            }
            if (isBase64(rawTrans)) {
                rawTrans = decodeBase64(rawTrans)
            }

            if (rawLrc.isBlank()) return null

            val alignedPair = LyricAligner.alignLrcTimestamps(rawLrc, rawTrans)

            OnlineLyricsResult(
                songId = targetSong.optLong("songid", 0L),
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

    private fun isBase64(str: String): Boolean {
        if (str.length < 20) return false
        return !str.contains("[") && !str.contains("]") && (str.endsWith("=") || str.matches(Regex("""^[A-Za-z0-9+/=\r\n]+$""")))
    }

    private fun decodeBase64(str: String): String {
        return try {
            String(Base64.decode(str, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            str
        }
    }
}
