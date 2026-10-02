package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

object MusixmatchLyricsService {

    private const val BASE_URL = "https://apic.musixmatch.com/ws/1.1/"
    private const val APP_ID = "android-player-v1.0"
    private val HEADERS = mapOf(
        "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 13)",
        "Cookie" to "AWSELB=0; AWSELBCORS=0"
    )

    @Volatile
    private var cachedUserToken: String? = null

    private val TIMESTAMP_LINE_REGEX = Regex("""^(\[\d{2}:\d{2}(?:\.\d{1,3})?\])(.*)$""")

    private fun generateT(): String = UUID.randomUUID().toString().replace("-", "")

    suspend fun ensureUserToken(customToken: String? = null): String? = withContext(Dispatchers.IO) {
        if (!customToken.isNullOrBlank()) {
            return@withContext customToken.trim()
        }
        cachedUserToken?.let { return@withContext it }

        synchronized(this) {
            cachedUserToken?.let { return@withContext it }
            try {
                val t = generateT()
                val url = "${BASE_URL}token.get?user_language=en&app_id=$APP_ID&t=$t"
                val jsonStr = LinerNotesHttpClient.get(url, HEADERS) ?: return@withContext null
                val root = JSONObject(jsonStr)
                val header = root.optJSONObject("message")?.optJSONObject("header")
                val statusCode = header?.optInt("status_code", 0) ?: 0
                if (statusCode != 200) {
                    return@withContext null
                }

                val token = root.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optString("user_token", "")

                if (!token.isNullOrBlank() && token != "00000000000000000000000000000000000000000000000000000000") {
                    cachedUserToken = token
                    token
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun invalidateToken() {
        cachedUserToken = null
    }

    suspend fun fetchLyrics(
        trackTitle: String,
        artistName: String,
        customToken: String? = null,
        targetLanguage: String = "zh"
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            val token = ensureUserToken(customToken) ?: return@withContext null
            val cleanTitle = LyricSearchCleaner.cleanTrackTitle(trackTitle)
            val cleanArt = LyricSearchCleaner.cleanArtist(artistName)
            val encTrack = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = URLEncoder.encode(cleanArt, "UTF-8")
            val tSearch = generateT()

            val searchUrl = "${BASE_URL}track.search?page_size=5&page=1&s_track_rating=desc&q_track=$encTrack&q_artist=$encArtist&usertoken=$token&format=json&app_id=$APP_ID&t=$tSearch"
            val searchJson = LinerNotesHttpClient.get(searchUrl, HEADERS) ?: return@withContext null
            val searchRoot = JSONObject(searchJson)

            val statusCode = searchRoot.optJSONObject("message")
                ?.optJSONObject("header")
                ?.optInt("status_code", 0) ?: 0

            if (statusCode == 401) {
                invalidateToken()
                return@withContext null
            }

            val trackList = searchRoot.optJSONObject("message")
                ?.optJSONObject("body")
                ?.optJSONArray("track_list") ?: return@withContext null

            if (trackList.length() == 0) return@withContext null

            var bestTrackObj: JSONObject? = null
            for (i in 0 until trackList.length()) {
                val item = trackList.optJSONObject(i)?.optJSONObject("track") ?: continue
                if (item.optInt("has_subtitles", 0) == 1) {
                    bestTrackObj = item
                    break
                }
            }
            if (bestTrackObj == null) {
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i)?.optJSONObject("track") ?: continue
                    if (item.optInt("has_lyrics", 0) == 1) {
                        bestTrackObj = item
                        break
                    }
                }
            }
            if (bestTrackObj == null) {
                bestTrackObj = trackList.optJSONObject(0)?.optJSONObject("track") ?: return@withContext null
            }

            val trackId = bestTrackObj.optLong("track_id", 0L)
            val matchedTitle = bestTrackObj.optString("track_name", trackTitle)
            val matchedArtist = bestTrackObj.optString("artist_name", artistName)
            val hasSubtitles = bestTrackObj.optInt("has_subtitles", 0) == 1

            if (trackId == 0L) return@withContext null

            var originalLyrics: String? = null

            // 1. 获取 LRC 同步时间戳字幕
            if (hasSubtitles) {
                val tSub = generateT()
                val subUrl = "${BASE_URL}track.subtitle.get?subtitle_format=lrc&track_id=$trackId&usertoken=$token&format=json&app_id=$APP_ID&t=$tSub"
                val subJson = LinerNotesHttpClient.get(subUrl, HEADERS)
                if (subJson != null) {
                    val subRoot = JSONObject(subJson)
                    val subBody = subRoot.optJSONObject("message")
                        ?.optJSONObject("body")
                        ?.optJSONObject("subtitle")
                        ?.optString("subtitle_body", "")
                    if (!subBody.isNullOrBlank()) {
                        originalLyrics = subBody.trim()
                    }
                }
            }

            // 2. 无同步时间戳时回退纯文本歌词
            if (originalLyrics.isNullOrBlank()) {
                val tLyr = generateT()
                val lyrUrl = "${BASE_URL}track.lyrics.get?track_id=$trackId&usertoken=$token&format=json&app_id=$APP_ID&t=$tLyr"
                val lyrJson = LinerNotesHttpClient.get(lyrUrl, HEADERS)
                if (lyrJson != null) {
                    val lyrRoot = JSONObject(lyrJson)
                    val lyrBody = lyrRoot.optJSONObject("message")
                        ?.optJSONObject("body")
                        ?.optJSONObject("lyrics")
                        ?.optString("lyrics_body", "")
                    if (!lyrBody.isNullOrBlank()) {
                        val cleanBody = lyrBody.lines()
                            .filterNot { it.contains("This Lyrics is NOT for Commercial use", ignoreCase = true) }
                            .joinToString("\n")
                            .trim()
                        if (cleanBody.isNotBlank()) {
                            originalLyrics = cleanBody
                        }
                    }
                }
            }

            if (originalLyrics.isNullOrBlank()) return@withContext null

            // 3. 尝试拉取众包翻译
            var translatedLyrics: String? = null
            val tTrans = generateT()
            val transUrl = "${BASE_URL}crowd.track.translations.get?track_id=$trackId&selected_language=$targetLanguage&usertoken=$token&format=json&app_id=$APP_ID&t=$tTrans"
            val transJson = LinerNotesHttpClient.get(transUrl, HEADERS)
            if (transJson != null) {
                try {
                    val transRoot = JSONObject(transJson)
                    val transList = transRoot.optJSONObject("message")
                        ?.optJSONObject("body")
                        ?.optJSONArray("translations_list")

                    if (transList != null && transList.length() > 0) {
                        val transMap = mutableMapOf<String, String>()
                        for (i in 0 until transList.length()) {
                            val tObj = transList.optJSONObject(i)?.optJSONObject("translation") ?: continue
                            val originalSnippet = tObj.optString("snippet", "").trim()
                            val translatedSnippet = tObj.optString("description", "").trim()
                            if (originalSnippet.isNotBlank() && translatedSnippet.isNotBlank()) {
                                transMap[originalSnippet] = translatedSnippet
                            }
                        }

                        if (transMap.isNotEmpty()) {
                            val transLines = mutableListOf<String>()
                            val origLines = originalLyrics.lines()
                            for (line in origLines) {
                                val match = TIMESTAMP_LINE_REGEX.find(line)
                                if (match != null) {
                                    val timestamp = match.groupValues[1]
                                    val text = match.groupValues[2].trim()
                                    val transText = transMap[text] ?: ""
                                    transLines.add("$timestamp$transText")
                                } else {
                                    val transText = transMap[line.trim()] ?: ""
                                    transLines.add(transText)
                                }
                            }
                            val candidateTrans = transLines.joinToString("\n").trim()
                            if (candidateTrans.lines().any { it.replace(TIMESTAMP_LINE_REGEX, "$2").isNotBlank() }) {
                                translatedLyrics = candidateTrans
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略翻译解析错误
                }
            }

            val alignedPair = LyricAligner.alignLrcTimestamps(originalLyrics, translatedLyrics)

            OnlineLyricsResult(
                songId = trackId,
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
}
