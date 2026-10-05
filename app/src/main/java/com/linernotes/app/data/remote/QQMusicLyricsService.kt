package com.linernotes.app.data.remote

import android.util.Base64
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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


    suspend fun fetchLyricByMid(
        songmid: String,
        fallbackTitle: String = "",
        fallbackArtist: String = "",
        albummid: String = "",
        durationMs: Long = 0L
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        if (songmid.isBlank()) return@withContext null
        val matchedCover = if (albummid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg" else null
        val lyricUrl = "$LYRIC_API?songmid=$songmid&format=json&nobase64=1"
        val lyricJson = LinerNotesHttpClient.getAsync(lyricUrl, LYRIC_HEADERS) ?: return@withContext null
        return@withContext try {
            val lyricRoot = JSONObject(lyricJson)
            var rawLrc = lyricRoot.optString("lyric", "")
            var rawTrans = lyricRoot.optString("trans", "")
            if (isBase64(rawLrc)) rawLrc = decodeBase64(rawLrc)
            if (isBase64(rawTrans)) rawTrans = decodeBase64(rawTrans)
            if (rawLrc.isBlank()) return@withContext null
            val alignedPair = LyricAligner.alignLrcTimestamps(rawLrc, rawTrans)
            OnlineLyricsResult(
                songId = 0L,
                title = fallbackTitle,
                artist = fallbackArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = alignedPair.second.ifBlank { null },
                isBilingual = alignedPair.second.isNotBlank(),
                coverUrl = matchedCover,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchRawCandidates(query: String, limit: Int = 8): List<com.linernotes.app.domain.model.LyricCandidateItem> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$SEARCH_API?p=1&n=$limit&w=$encodedQuery&format=json"
            val searchJson = LinerNotesHttpClient.getAsync(searchUrl, SEARCH_HEADERS) ?: return@withContext emptyList()
            val searchRoot = JSONObject(searchJson)
            val songList = searchRoot.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return@withContext emptyList()
            val list = mutableListOf<com.linernotes.app.domain.model.LyricCandidateItem>()
            for (i in 0 until songList.length()) {
                val s = songList.optJSONObject(i) ?: continue
                val songmid = s.optString("songmid", "")
                if (songmid.isBlank()) continue
                val title = s.optString("songname", "")
                val singers = s.optJSONArray("singer")
                val artist = if (singers != null) {
                    (0 until singers.length()).mapNotNull { idx -> singers.optJSONObject(idx)?.optString("name") }.joinToString(", ")
                } else ""
                val album = s.optString("albumname", "")
                val albummid = s.optString("albummid", "")
                val cover = if (albummid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg" else null
                val durationMs = s.optLong("interval", 0L) * 1000L
                list.add(
                    com.linernotes.app.domain.model.LyricCandidateItem(
                        source = com.linernotes.app.domain.model.LyricSource.QQ_MUSIC,
                        sourceId = songmid,
                        extraKey = albummid,
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
                        val lyricUrl = "$LYRIC_API?songmid=${item.sourceId}&format=json&nobase64=1"
                        val json = LinerNotesHttpClient.getAsync(lyricUrl, LYRIC_HEADERS)
                        if (json != null) {
                            try {
                                val root = JSONObject(json)
                                var trans = root.optString("trans", "")
                                if (isBase64(trans)) trans = decodeBase64(trans)
                                if (trans.isNotBlank()) {
                                    item.copy(hasTranslation = true)
                                } else item
                            } catch (e: Exception) { item }
                        } else item
                    }
                }.awaitAll()
            } + list.drop(6)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class ScoredCandidate(
        val songJson: JSONObject,
        val score: Int
    )

    private suspend fun fetchSongLyricsByMid(
        targetSong: JSONObject,
        score: Int,
        targetTitle: String,
        targetArtist: String
    ): OnlineLyricsResult? {
        val songmid = targetSong.optString("songmid", "")
        if (songmid.isBlank()) return null

        val matchedTitle = targetSong.optString("songname", targetTitle)
        val matchedArtist = targetSong.optJSONArray("singer")?.optJSONObject(0)?.optString("name", targetArtist) ?: targetArtist
        val albummid = targetSong.optString("albummid", "")
        val matchedCover = if (albummid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg" else null
        val matchedDuration = targetSong.optLong("interval", 0L) * 1000L

        val lyricUrl = "$LYRIC_API?songmid=$songmid&format=json&nobase64=1"
        val lyricJson = LinerNotesHttpClient.getAsync(lyricUrl, LYRIC_HEADERS) ?: return null

        return try {
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

    private suspend fun searchAndFetch(
        query: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$SEARCH_API?p=1&n=5&w=$encodedQuery&format=json"
            val searchJson = LinerNotesHttpClient.getAsync(searchUrl, SEARCH_HEADERS) ?: return null

            val searchRoot = JSONObject(searchJson)
            val dataObj = searchRoot.optJSONObject("data") ?: return null
            val songObj = dataObj.optJSONObject("song") ?: return null
            val songList = songObj.optJSONArray("list") ?: return null
            if (songList.length() == 0) return null

            val scoredList = mutableListOf<ScoredCandidate>()

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

                if (score >= 45) {
                    scoredList.add(ScoredCandidate(s, score))
                }
            }

            if (scoredList.isEmpty()) return null
            scoredList.sortByDescending { it.score }

            // 多候选版本深度嗅探（并发探测前 4 个高分版本，优先捕获官方人工双语精翻）
            val topCandidates = scoredList.take(4)
            val fetchedResults = supervisorScope {
                topCandidates.map { cand ->
                    async {
                        fetchSongLyricsByMid(cand.songJson, cand.score, targetTitle, targetArtist)?.let { res ->
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
