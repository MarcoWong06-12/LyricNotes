package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.lyric.LyricSearchCleaner
import com.linernotes.app.core.network.LinerNotesHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.Inflater

object KugouLyricsService {

    private const val SEARCH_API = "https://songsearch.kugou.com/song_search_v2"
    private const val CANDIDATE_API = "https://krcs.kugou.com/search"
    private const val DOWNLOAD_API = "https://krcs.kugou.com/download"
    private const val MAX_DECOMPRESSED_KRC_BYTES = 2 * 1024 * 1024

    private val KRC_MAGIC = byteArrayOf('k'.code.toByte(), 'r'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte())
    private val KRC_XOR_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77,
        0x5E, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2D,
        0xCE.toByte(), 0xD2.toByte(), 0x6E, 0x69
    )
    private val KRC_LINE_AND_WORD_REGEX = Regex("""\[\d+,\d+\].*<\d+,\d+""")
    private val KRC_TIME_HEADER_REGEX = Regex("""^\[(\d+),(\d+)\]""")
    private val KRC_LANGUAGE_TAG_REGEX = Regex("""\[language:([A-Za-z0-9+/=\r\n]+)\]""")
    private val LRC_OR_KRC_TIME_REGEX = Regex("""\[(?:\d{1,3}:\d{2}(?:\.\d{1,3})?|\d+,\d+)\]""")

    private val HEADERS = mapOf(
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

    private suspend fun searchAndFetch(
        query: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long = 0L
    ): OnlineLyricsResult? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$SEARCH_API?keyword=$encodedQuery&page=1&pagesize=5&platform=WebFilter"
            val searchJson = LinerNotesHttpClient.getAsync(searchUrl, HEADERS) ?: return null

            val searchRoot = JSONObject(searchJson)
            val dataObj = searchRoot.optJSONObject("data") ?: return null
            val list = dataObj.optJSONArray("lists") ?: return null
            if (list.length() == 0) return null

            var targetSong: JSONObject? = null
            var bestScore = -100

            for (i in 0 until list.length()) {
                val s = list.optJSONObject(i) ?: continue
                val candTitle = s.optString("SongName", "")
                val candArtist = s.optString("SingerName", "")
                val candDuration = s.optLong("Duration", 0L) * 1000L

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

            val hash = targetSong.optString("FileHash", "")
            val duration = targetSong.optLong("Duration", 0L) * 1000L
            val matchedTitle = targetSong.optString("SongName", targetTitle)
            val matchedArtist = targetSong.optString("SingerName", targetArtist)

            if (hash.isBlank()) return null

            // 检索歌词候选集
            val encTitle = URLEncoder.encode(LyricSearchCleaner.cleanTrackTitle(targetTitle), "UTF-8")
            val candUrl = "$CANDIDATE_API?ver=1&man=yes&client=mobi&keyword=$encTitle&duration=$duration&hash=$hash"
            val candJson = LinerNotesHttpClient.getAsync(candUrl, HEADERS) ?: return null
            val candRoot = JSONObject(candJson)
            val candidates = candRoot.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val cand = candidates.optJSONObject(0) ?: return null
            val id = cand.optString("id", "")
            val accesskey = cand.optString("accesskey", "")
            if (id.isBlank() || accesskey.isBlank()) return null

            val rawLrc = fetchKrcOrLrcContent(id, accesskey) ?: return null
            val krcTrans = extractKrcLanguageTranslation(rawLrc)

            val alignedPair = LyricAligner.alignLrcTimestamps(rawLrc, krcTrans)

            OnlineLyricsResult(
                songId = 0L,
                title = matchedTitle,
                artist = matchedArtist,
                originalLyrics = alignedPair.first,
                translatedLyrics = alignedPair.second.ifBlank { null },
                isBilingual = alignedPair.second.isNotBlank(),
                coverUrl = null,
                durationMs = duration
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchKrcOrLrcContent(id: String, accesskey: String): String? {
        // 优先尝试 KRC 逐字歌词并严格解码校验，失败则安全回退到标准 LRC
        val krcUrl = "$DOWNLOAD_API?ver=1&client=mobi&id=$id&accesskey=$accesskey&fmt=krc&charset=utf8"
        val krcJson = LinerNotesHttpClient.getAsync(krcUrl, HEADERS)
        if (!krcJson.isNullOrBlank()) {
            val krcRoot = runCatching { JSONObject(krcJson) }.getOrNull()
            val b64Krc = krcRoot?.optString("content", "").orEmpty()
            val returnedFmt = krcRoot?.optString("fmt", "")?.lowercase().orEmpty()
            if (b64Krc.isNotBlank()) {
                val decodedKrc = decodeKrc(b64Krc)
                if (decodedKrc != null) return decodedKrc
                if (returnedFmt == "lrc") {
                    val decodedAsLrc = decodePlainBase64(b64Krc)
                    if (isSafePlainLyrics(decodedAsLrc)) return decodedAsLrc
                }
            }
        }

        val lrcUrl = "$DOWNLOAD_API?ver=1&client=mobi&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8"
        val lrcJson = LinerNotesHttpClient.getAsync(lrcUrl, HEADERS) ?: return null
        val lrcRoot = JSONObject(lrcJson)
        val b64Lrc = lrcRoot.optString("content", "")
        if (b64Lrc.isBlank()) return null

        val decodedLrc = decodePlainBase64(b64Lrc)
        return decodedLrc?.takeIf { isSafePlainLyrics(it) }
    }

    private fun isKugouTranslationDisclaimer(text: String): Boolean {
        val compact = text.trim()
        return compact.contains("大模型提供") ||
            (compact.contains("翻译") && compact.contains("提供")) ||
            compact.startsWith("以下歌词翻译由")
    }

    /**
     * 解析酷狗 KRC 头部内嵌的 [language:base64] 多语翻译字段（type == 1 为译文），
     * 并与 KRC 逐行时间轴按行号 1:1 对应为标准时间戳 LRC 译文。
     */
    internal fun extractKrcLanguageTranslation(decodedKrc: String): String? {
        val b64 = KRC_LANGUAGE_TAG_REGEX.find(decodedKrc)?.groupValues?.getOrNull(1)
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val krcTimedStarts = decodedKrc.lines().mapNotNull { line ->
            val trimmed = line.trim()
            KRC_TIME_HEADER_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
        if (krcTimedStarts.isEmpty()) return null

        return try {
            val langJson = String(Base64.getMimeDecoder().decode(b64), Charsets.UTF_8)
            val contentArr = JSONObject(langJson).optJSONArray("content") ?: return null
            var translationContent: org.json.JSONArray? = null
            for (i in 0 until contentArr.length()) {
                val entry = contentArr.optJSONObject(i) ?: continue
                if (entry.optInt("type", -1) == 1) {
                    translationContent = entry.optJSONArray("lyricContent")
                    if (translationContent != null) break
                }
            }
            if (translationContent == null || translationContent.length() != krcTimedStarts.size) {
                return null
            }

            val out = StringBuilder()
            for (i in 0 until translationContent.length()) {
                val row = translationContent.optJSONArray(i) ?: continue
                val lineText = buildString {
                    for (j in 0 until row.length()) {
                        val part = row.optString(j, "").trim()
                        if (part.isNotEmpty()) {
                            if (isNotEmpty()) append(' ')
                            append(part)
                        }
                    }
                }.trim()
                if (lineText.isBlank() || isKugouTranslationDisclaimer(lineText)) continue
                out.append(LyricAligner.formatTimestamp(krcTimedStarts[i]))
                    .append(' ')
                    .append(lineText)
                    .append('\n')
            }
            out.toString().trimEnd().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    internal fun decodeKrc(b64Content: String): String? {
        val cleanB64 = b64Content.trim()
        if (cleanB64.isEmpty()) return null
        val rawBytes = try {
            Base64.getMimeDecoder().decode(cleanB64)
        } catch (_: Exception) {
            return null
        }
        if (rawBytes.size <= KRC_MAGIC.size) return null
        for (i in KRC_MAGIC.indices) {
            if (rawBytes[i] != KRC_MAGIC[i]) return null
        }

        val encryptedSize = rawBytes.size - KRC_MAGIC.size
        val deflated = ByteArray(encryptedSize)
        for (i in 0 until encryptedSize) {
            deflated[i] = (rawBytes[i + KRC_MAGIC.size].toInt() xor KRC_XOR_KEY[i % KRC_XOR_KEY.size].toInt()).toByte()
        }

        val inflater = Inflater()
        val decompressedBytes = try {
            inflater.setInput(deflated)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    continue
                }
                total += count
                if (total > MAX_DECOMPRESSED_KRC_BYTES) return null
                out.write(buffer, 0, count)
            }
            if (!inflater.finished()) return null
            out.toByteArray()
        } catch (_: Exception) {
            return null
        } finally {
            inflater.end()
        }

        val decoded = String(decompressedBytes, Charsets.UTF_8)
            .removePrefix("\uFEFF")
            .trim()
        return decoded.takeIf { isValidDecodedKrc(it) }
    }

    internal fun isValidDecodedKrc(decoded: String?): Boolean {
        if (!isSafePlainLyrics(decoded)) return false
        return KRC_LINE_AND_WORD_REGEX.containsMatchIn(decoded!!)
    }

    internal fun isSafePlainLyrics(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        for (ch in text) {
            if (ch == '\u0000' || ch == '\uFFFD') return false
            if (ch < ' ' && ch != '\n' && ch != '\r' && ch != '\t') return false
            if (ch in '\u007F'..'\u009F') return false
        }
        val trimmed = text.trim()
        if (!trimmed.contains('\n') && !trimmed.contains(' ') && trimmed.length >= 64) {
            return false
        }
        if (trimmed.length >= 32 && trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it.isWhitespace() }) {
            return false
        }
        return LRC_OR_KRC_TIME_REGEX.containsMatchIn(trimmed) || trimmed.any { it.isLetter() }
    }

    private fun decodePlainBase64(b64Content: String): String? {
        return try {
            String(Base64.getMimeDecoder().decode(b64Content.trim()), Charsets.UTF_8)
                .removePrefix("\uFEFF")
                .trim()
                .ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
