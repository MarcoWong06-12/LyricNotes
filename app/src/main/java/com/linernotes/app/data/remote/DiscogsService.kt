package com.linernotes.app.data.remote

import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class DiscogsReleaseSummary(
    val id: Long,
    val title: String,
    val artist: String?,
    val year: String?,
    val country: String?,
    val formats: List<String>,
    val primaryFormat: String, // e.g. "SHM-CD", "Digipak", "CD", "SACD"
    val label: String?,
    val catno: String?,
    val barcode: String?,
    val thumbUrl: String?,
    val coverImageUrl: String?
)

data class DiscogsReleaseDetail(
    val id: Long,
    val title: String,
    val artist: String,
    val year: String?,
    val country: String?,
    val releasedDate: String?,
    val label: String?,
    val catalogNumber: String?,
    val barcode: String?,
    val formats: List<String>,
    val mediaType: String,
    val genres: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    val rating: Float? = null,
    val haveCount: Int? = null,
    val notes: String?,
    val coverUrl: String?,
    val bookletImageUrls: List<String>,
    val tracklist: List<DiscogsTrackItem>,
    val credits: List<DiscogsCreditItem>
)

data class DiscogsTrackItem(
    val position: String,
    val trackNumber: Int,
    val title: String,
    val duration: String?,
    val durationMs: Long?
)

data class DiscogsCreditItem(
    val role: String,
    val name: String
)

object DiscogsService {

    private const val BASE_URL = "https://api.discogs.com"
    private const val USER_AGENT = "LinerNotes/1.0 (Android; +https://github.com/MarcoWong06-12/LinerNotes)"

    private fun buildHeaders(token: String?): Map<String, String> {
        val headers = mutableMapOf("User-Agent" to USER_AGENT)
        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Discogs token=${token.trim()}"
        }
        return headers
    }

    /**
     * 根据专辑名称与艺术家检索实体 CD 版本列表
     */
    suspend fun searchReleasesByAlbumAndArtist(
        albumTitle: String,
        artist: String,
        token: String? = null
    ): List<DiscogsReleaseSummary> = withContext(Dispatchers.IO) {
        val cleanTitle = albumTitle.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank()) return@withContext emptyList()

        val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
        val encArtist = if (cleanArtist.isNotBlank() && cleanArtist != "未知艺术家" && cleanArtist != "Unknown Artist") {
            URLEncoder.encode(cleanArtist, "UTF-8")
        } else null

        val url = if (encArtist != null) {
            "$BASE_URL/database/search?release_title=$encTitle&artist=$encArtist&format=CD&type=release&per_page=30"
        } else {
            "$BASE_URL/database/search?q=$encTitle&format=CD&type=release&per_page=30"
        }

        searchReleasesInternal(url, token)
    }

    /**
     * 根据关键词检索（支持曲目名、专辑名、唱片编号或艺人）
     */
    suspend fun searchReleases(
        query: String,
        token: String? = null
    ): List<DiscogsReleaseSummary> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(clean, "UTF-8")
        val url = "$BASE_URL/database/search?q=$encoded&format=CD&type=release&per_page=30"
        searchReleasesInternal(url, token)
    }

    /**
     * 根据唱片条形码（UPC / EAN Barcode）精准查找压盘版本
     */
    suspend fun searchByBarcode(
        barcode: String,
        token: String? = null
    ): List<DiscogsReleaseSummary> = withContext(Dispatchers.IO) {
        val clean = barcode.replace("[^0-9]".toRegex(), "").trim()
        if (clean.isBlank()) return@withContext emptyList()

        val url = "$BASE_URL/database/search?barcode=$clean&type=release&per_page=15"
        searchReleasesInternal(url, token)
    }

    private fun searchReleasesInternal(url: String, token: String?): List<DiscogsReleaseSummary> {
        return try {
            val jsonStr = LinerNotesHttpClient.get(url, buildHeaders(token)) ?: return emptyList()
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<DiscogsReleaseSummary>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                if (id <= 0L) continue

                val rawTitle = item.optString("title", "")
                var artist: String? = null
                var albumName = rawTitle

                if (rawTitle.contains(" - ")) {
                    val parts = rawTitle.split(" - ", limit = 2)
                    artist = parts[0].trim()
                    albumName = parts[1].trim()
                }

                val year = item.optString("year", "").takeIf { it.isNotBlank() && it != "0" }
                val country = item.optString("country", "").takeIf { it.isNotBlank() }

                val formatArray = item.optJSONArray("format") ?: JSONArray()
                val formats = mutableListOf<String>()
                for (f in 0 until formatArray.length()) {
                    formats.add(formatArray.optString(f))
                }

                val labelArray = item.optJSONArray("label") ?: JSONArray()
                val label = labelArray.optString(0, "").takeIf { it.isNotBlank() }

                val catno = item.optString("catno", "").takeIf { it.isNotBlank() && it != "none" }
                val barcodeArray = item.optJSONArray("barcode") ?: JSONArray()
                val barcode = barcodeArray.optString(0, "").takeIf { it.isNotBlank() }

                val thumb = item.optString("thumb", "").takeIf { it.isNotBlank() }
                val coverImage = item.optString("cover_image", "").takeIf { it.isNotBlank() }

                val primaryFormat = determinePrimaryFormat(formats)

                list.add(
                    DiscogsReleaseSummary(
                        id = id,
                        title = albumName,
                        artist = artist,
                        year = year,
                        country = country,
                        formats = formats,
                        primaryFormat = primaryFormat,
                        label = label,
                        catno = catno,
                        barcode = barcode,
                        thumbUrl = thumb,
                        coverImageUrl = coverImage
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取指定 Release 版本的完整详细信息（曲目名单、各曲精确时长、内页扫描图、演职人员制作名单）
     */
    suspend fun fetchReleaseDetail(
        releaseId: Long,
        token: String? = null
    ): DiscogsReleaseDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/releases/$releaseId"
            val jsonStr = LinerNotesHttpClient.get(url, buildHeaders(token)) ?: return@withContext null
            val root = JSONObject(jsonStr)

            val id = root.optLong("id", releaseId)
            val title = root.optString("title", "")
            
            // 艺术家提取
            val artistsArray = root.optJSONArray("artists") ?: JSONArray()
            val artistNames = mutableListOf<String>()
            for (a in 0 until artistsArray.length()) {
                val artObj = artistsArray.optJSONObject(a) ?: continue
                val name = artObj.optString("name", "").replace("\\s*\\(\\d+\\)$".toRegex(), "").trim()
                if (name.isNotBlank()) artistNames.add(name)
            }
            val artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

            val year = root.optString("year", "").takeIf { it.isNotBlank() && it != "0" }
            val country = root.optString("country", "").takeIf { it.isNotBlank() }
            val released = root.optString("released", "").takeIf { it.isNotBlank() }

            // 厂牌与唱片编号
            val labelsArray = root.optJSONArray("labels") ?: JSONArray()
            val labelNames = mutableListOf<String>()
            var primaryCatno: String? = null
            for (l in 0 until labelsArray.length()) {
                val lblObj = labelsArray.optJSONObject(l) ?: continue
                val name = lblObj.optString("name", "").trim()
                val cat = lblObj.optString("catno", "").takeIf { it.isNotBlank() && it != "none" }
                if (name.isNotBlank()) {
                    labelNames.add(if (cat != null) "$name ($cat)" else name)
                }
                if (primaryCatno == null && cat != null) {
                    primaryCatno = cat
                }
            }
            val labelStr = labelNames.firstOrNull()

            // 条形码
            val identifiersArray = root.optJSONArray("identifiers") ?: JSONArray()
            var barcode: String? = null
            for (idx in 0 until identifiersArray.length()) {
                val ident = identifiersArray.optJSONObject(idx) ?: continue
                if (ident.optString("type").equals("Barcode", ignoreCase = true)) {
                    val rawVal = ident.optString("value", "").replace("[^0-9]".toRegex(), "")
                    if (rawVal.isNotBlank()) {
                        barcode = rawVal
                        break
                    }
                }
            }

            // 包装与格式
            val formatsArray = root.optJSONArray("formats") ?: JSONArray()
            val formatList = mutableListOf<String>()
            for (f in 0 until formatsArray.length()) {
                val fObj = formatsArray.optJSONObject(f) ?: continue
                val fName = fObj.optString("name", "")
                if (fName.isNotBlank()) formatList.add(fName)
                val descArray = fObj.optJSONArray("descriptions") ?: JSONArray()
                for (d in 0 until descArray.length()) {
                    formatList.add(descArray.optString(d))
                }
            }
            val mediaType = determinePrimaryFormat(formatList)

            // 官方文案 / 备注文档
            val notes = root.optString("notes", "").takeIf { it.isNotBlank() }

            // 图片与扫描件
            val imagesArray = root.optJSONArray("images") ?: JSONArray()
            var coverUrl: String? = null
            val bookletImages = mutableListOf<String>()
            for (imgIdx in 0 until imagesArray.length()) {
                val imgObj = imagesArray.optJSONObject(imgIdx) ?: continue
                val uri = imgObj.optString("uri", "")
                val type = imgObj.optString("type", "")
                if (uri.isNotBlank()) {
                    if (type == "primary" && coverUrl == null) {
                        coverUrl = uri
                    } else {
                        bookletImages.add(uri)
                    }
                }
            }
            if (coverUrl == null && bookletImages.isNotEmpty()) {
                coverUrl = bookletImages.removeAt(0)
            }

            // 曲目列表
            val tracklistArray = root.optJSONArray("tracklist") ?: JSONArray()
            val tracks = mutableListOf<DiscogsTrackItem>()
            var autoTrackNum = 1
            for (t in 0 until tracklistArray.length()) {
                val tObj = tracklistArray.optJSONObject(t) ?: continue
                val type = tObj.optString("type_", "track")
                if (type == "heading") continue // 跳过大碟分组副标题

                val rawPos = tObj.optString("position", "").trim()
                val parsedNum = rawPos.toIntOrNull() ?: autoTrackNum
                val trackTitle = tObj.optString("title", "Track $parsedNum").trim()
                val durationStr = tObj.optString("duration", "").takeIf { it.isNotBlank() }
                val durationMs = parseDurationToMs(durationStr)

                tracks.add(
                    DiscogsTrackItem(
                        position = if (rawPos.isNotBlank()) rawPos else parsedNum.toString(),
                        trackNumber = parsedNum,
                        title = trackTitle,
                        duration = durationStr,
                        durationMs = durationMs
                    )
                )
                autoTrackNum++
            }

            // 演职员表与鸣谢
            val creditsArray = root.optJSONArray("extraartists") ?: JSONArray()
            val credits = mutableListOf<DiscogsCreditItem>()
            for (c in 0 until creditsArray.length()) {
                val cObj = creditsArray.optJSONObject(c) ?: continue
                val cName = cObj.optString("name", "").replace("\\s*\\(\\d+\\)$".toRegex(), "").trim()
                val role = cObj.optString("role", "").trim()
                if (cName.isNotBlank() && role.isNotBlank()) {
                    credits.add(DiscogsCreditItem(role = role, name = cName))
                }
            }

            // 流派与风格
            val genresArray = root.optJSONArray("genres") ?: JSONArray()
            val genresList = mutableListOf<String>()
            for (g in 0 until genresArray.length()) {
                val gStr = genresArray.optString(g, "").trim()
                if (gStr.isNotBlank()) genresList.add(gStr)
            }

            val stylesArray = root.optJSONArray("styles") ?: JSONArray()
            val stylesList = mutableListOf<String>()
            for (s in 0 until stylesArray.length()) {
                val sStr = stylesArray.optString(s, "").trim()
                if (sStr.isNotBlank()) stylesList.add(sStr)
            }

            // 制作与制造单位 (Companies / Studios)
            val companiesArray = root.optJSONArray("companies") ?: JSONArray()
            val companiesList = mutableListOf<String>()
            for (c in 0 until companiesArray.length()) {
                val cObj = companiesArray.optJSONObject(c) ?: continue
                val entityType = cObj.optString("entity_type_name", "").trim()
                val cName = cObj.optString("name", "").replace("\\s*\\(\\d+\\)$".toRegex(), "").trim()
                if (cName.isNotBlank()) {
                    companiesList.add(if (entityType.isNotBlank()) "$entityType: $cName" else cName)
                }
            }

            // 社区评分与收藏数
            val communityObj = root.optJSONObject("community")
            val ratingAvg = communityObj?.optJSONObject("rating")?.optDouble("average", 0.0)?.toFloat()?.takeIf { it > 0f }
            val have = communityObj?.optInt("have", 0)?.takeIf { it > 0 }

            DiscogsReleaseDetail(
                id = id,
                title = title,
                artist = artist,
                year = year,
                country = country,
                releasedDate = released,
                label = labelStr,
                catalogNumber = primaryCatno,
                barcode = barcode,
                formats = formatList,
                mediaType = mediaType,
                genres = genresList,
                styles = stylesList,
                companies = companiesList,
                rating = ratingAvg,
                haveCount = have,
                notes = notes,
                coverUrl = coverUrl,
                bookletImageUrls = bookletImages,
                tracklist = tracks,
                credits = credits
            )
        } catch (e: Exception) {
            null
        }
    }

    internal fun determinePrimaryFormat(formats: List<String>): String {
        val lower = formats.map { it.lowercase() }
        return when {
            lower.any { it.contains("shm-cd") } -> "SHM-CD"
            lower.any { it.contains("sacd") } -> "SACD"
            lower.any { it.contains("xrcd") } -> "XRCD"
            lower.any { it.contains("blu-spec") } -> "BSCD2"
            lower.any { it.contains("hdcd") } -> "HDCD"
            lower.any { it.contains("digipak") } -> "Digipak CD"
            lower.any { it.contains("cardboard") || it.contains("mini-lp") || it.contains("paper sleeve") } -> "Mini-LP 纸套 CD"
            lower.any { it.contains("box set") } -> "Box Set 盒装 CD"
            else -> "标准 CD"
        }
    }

    internal fun parseDurationToMs(duration: String?): Long? {
        if (duration.isNullOrBlank()) return null
        return try {
            val parts = duration.split(":").map { it.trim().toLong() }
            when (parts.size) {
                2 -> (parts[0] * 60 + parts[1]) * 1000L
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
