package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import com.linernotes.app.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class OnlineAlbumInfo(
    val collectionId: Long,
    val source: String = "iTunes", // "iTunes" or "NetEase"
    val title: String,
    val artist: String,
    val releaseYear: String,
    val coverUrl: String,
    val trackCount: Int
)

object MetadataService {

    private val HEADERS = mapOf(
        "User-Agent" to "LinerNotes/1.0 (Android; https://github.com/MarcoWong06-12/LinerNotes)"
    )

    private val NETEASE_HEADERS = mapOf(
        "Referer" to "https://music.163.com/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    /**
     * 并发从网易云音乐与 Apple Music / iTunes 检索专辑候选列表
     */
    suspend fun searchAlbums(query: String): List<OnlineAlbumInfo> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val neteaseDeferred = async { searchNetEaseAlbums(trimmed) }
        val itunesDeferred = async { searchItunesAlbums(trimmed) }

        val neteaseList = try { neteaseDeferred.await() } catch (e: Exception) { emptyList() }
        val itunesList = try { itunesDeferred.await() } catch (e: Exception) { emptyList() }

        val combined = mutableListOf<OnlineAlbumInfo>()
        // 若查询包含日文假名或中文，优先展示网易云原生专辑名，两者皆收录
        combined.addAll(neteaseList)
        combined.addAll(itunesList)

        // 依据 source 与 id 简单去重
        val seen = mutableSetOf<String>()
        combined.filter { album ->
            val key = "${album.source}:${album.collectionId}"
            seen.add(key)
        }.take(15)
    }

    suspend fun searchAlbum(query: String): OnlineAlbumInfo? {
        return searchAlbums(query).firstOrNull()
    }

    private fun searchNetEaseAlbums(query: String): List<OnlineAlbumInfo> {
        return try {
            val postParams = mapOf(
                "s" to query,
                "type" to "10",
                "offset" to "0",
                "limit" to "10"
            )
            val jsonStr = LinerNotesHttpClient.postForm(
                "https://music.163.com/api/cloudsearch/pc",
                postParams,
                NETEASE_HEADERS
            ) ?: return emptyList()

            val root = JSONObject(jsonStr)
            val result = root.optJSONObject("result") ?: return emptyList()
            val albums = result.optJSONArray("albums") ?: return emptyList()

            val list = mutableListOf<OnlineAlbumInfo>()
            for (i in 0 until albums.length()) {
                val item = albums.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                if (id <= 0L) continue

                val title = item.optString("name", "").trim()
                if (title.isBlank()) continue

                val artistObj = item.optJSONObject("artist")
                val artistName = artistObj?.optString("name", "")?.trim()
                    ?: item.optJSONArray("artists")?.optJSONObject(0)?.optString("name", "")?.trim()
                    ?: "未知艺术家"

                val publishTime = item.optLong("publishTime", 0L)
                val year = if (publishTime > 0L) {
                    val cal = java.util.Calendar.getInstance()
                    cal.timeInMillis = publishTime
                    cal.get(java.util.Calendar.YEAR).toString()
                } else "未知年份"

                var rawCover = item.optString("picUrl", "")
                if (rawCover.startsWith("http://")) {
                    rawCover = rawCover.replaceFirst("http://", "https://")
                }
                val coverUrl = if (rawCover.isNotBlank() && !rawCover.contains("?param=")) {
                    "$rawCover?param=600y600"
                } else rawCover

                val trackCount = item.optInt("size", 0)

                list.add(
                    OnlineAlbumInfo(
                        collectionId = id,
                        source = "NetEase",
                        title = title,
                        artist = artistName,
                        releaseYear = year,
                        coverUrl = coverUrl,
                        trackCount = trackCount
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchItunesAlbums(query: String): List<OnlineAlbumInfo> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlsToTry = mutableListOf<String>()

            val hasJapanese = query.any { it in '\u3040'..'\u30ff' }
            val hasCjk = query.any { it in '\u4e00'..'\u9fff' }

            if (hasJapanese) {
                urlsToTry.add("https://itunes.apple.com/search?term=$encoded&entity=album&limit=8&country=JP")
            } else if (hasCjk) {
                urlsToTry.add("https://itunes.apple.com/search?term=$encoded&entity=album&limit=8&country=HK")
            }
            urlsToTry.add("https://itunes.apple.com/search?term=$encoded&entity=album&limit=8")

            val list = mutableListOf<OnlineAlbumInfo>()
            val seenIds = mutableSetOf<Long>()

            for (url in urlsToTry) {
                val jsonStr = LinerNotesHttpClient.get(url, HEADERS) ?: continue
                val root = JSONObject(jsonStr)
                val results = root.optJSONArray("results") ?: continue

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val collectionId = item.optLong("collectionId", 0L)
                    if (collectionId <= 0L || !seenIds.add(collectionId)) continue

                    val title = item.optString("collectionName", query)
                    val artist = item.optString("artistName", "Unknown Artist")
                    val releaseDate = item.optString("releaseDate", "")
                    val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "未知年份"
                    val rawCover = item.optString("artworkUrl100", "")
                    val coverUrl = rawCover.replace("100x100bb.jpg", "600x600bb.jpg")
                        .replace("100x100", "600x600")
                    val trackCount = item.optInt("trackCount", 0)

                    list.add(
                        OnlineAlbumInfo(
                            collectionId = collectionId,
                            source = "iTunes",
                            title = title,
                            artist = artist,
                            releaseYear = year,
                            coverUrl = coverUrl,
                            trackCount = trackCount
                        )
                    )
                }
                if (list.size >= 8) break
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTracksWithLyrics(
        collectionId: Long,
        source: String = "iTunes",
        albumId: String,
        artistName: String,
        albumTitle: String
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (source == "NetEase") {
            fetchNetEaseTracksWithLyrics(collectionId, albumId, artistName, albumTitle)
        } else {
            fetchItunesTracksWithLyrics(collectionId, albumId, artistName, albumTitle)
        }
    }

    private suspend fun fetchNetEaseTracksWithLyrics(
        albumIdNum: Long,
        localAlbumId: String,
        artistName: String,
        albumTitle: String
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.163.com/api/v1/album/$albumIdNum"
            val jsonStr = LinerNotesHttpClient.get(url, NETEASE_HEADERS) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)
            val songs = root.optJSONArray("songs") ?: return@withContext emptyList()

            val songList = mutableListOf<JSONObject>()
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                songList.add(song)
            }

            songList.sortBy { it.optInt("no", 1) }

            val deferredList = songList.mapIndexed { index, song ->
                async {
                    val songId = song.optLong("id", 0L)
                    val trackNum = song.optInt("no", index + 1)
                    val rawTrackName = song.optString("name", "Track $trackNum")
                    val cleanTrackName = LyricSearchCleaner.cleanTrackTitle(rawTrackName)
                    val duration = song.optLong("dt", 0L)

                    // 优先通过精准 NetEase songId 获取歌词与翻译，若无则回退多源 UnifiedLyricsService
                    val lyricResult = (if (songId > 0L) {
                        NetEaseLyricsService.fetchLyricById(songId, cleanTrackName, artistName)
                    } else null) ?: UnifiedLyricsService.fetchLyrics(cleanTrackName, artistName)

                    TrackEntity(
                        albumId = localAlbumId,
                        trackNumber = trackNum,
                        title = cleanTrackName,
                        translatedTitle = null,
                        originalLyrics = lyricResult?.originalLyrics,
                        translatedLyrics = lyricResult?.translatedLyrics,
                        durationMs = if (duration > 0) duration else null
                    )
                }
            }
            deferredList.awaitAll()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchItunesTracksWithLyrics(
        collectionId: Long,
        localAlbumId: String,
        artistName: String,
        albumTitle: String
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        try {
            val lookupUrl = "https://itunes.apple.com/lookup?id=$collectionId&entity=song"
            val jsonStr = LinerNotesHttpClient.get(lookupUrl, HEADERS) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val songs = mutableListOf<JSONObject>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optString("wrapperType") == "track") {
                    songs.add(item)
                }
            }

            songs.sortBy { it.optInt("trackNumber", 1) }

            // 并发加速检索全辑各曲目歌词
            val deferredList = songs.mapIndexed { index, song ->
                async {
                    val trackNum = song.optInt("trackNumber", index + 1)
                    val rawTrackName = song.optString("trackName", "Track $trackNum")
                    val cleanTrackName = LyricSearchCleaner.cleanTrackTitle(rawTrackName)
                    val duration = song.optLong("trackTimeMillis", 0L)

                    val lyricResult = UnifiedLyricsService.fetchLyrics(cleanTrackName, artistName)

                    TrackEntity(
                        albumId = localAlbumId,
                        trackNumber = trackNum,
                        title = cleanTrackName,
                        translatedTitle = null,
                        originalLyrics = lyricResult?.originalLyrics,
                        translatedLyrics = lyricResult?.translatedLyrics,
                        durationMs = if (duration > 0) duration else null
                    )
                }
            }

            deferredList.awaitAll()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
