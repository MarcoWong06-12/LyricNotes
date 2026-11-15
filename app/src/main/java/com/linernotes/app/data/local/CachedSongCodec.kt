package com.linernotes.app.data.local

import com.linernotes.app.data.local.entity.CachedSongEntity
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CachedSongPayload(
    val lyrics: List<BilingualLyricLine>,
    val annotatedLines: Map<Int, LyricAnnotationEntity>,
    val songStory: SongStoryEntity?,
    val geniusNotice: String? = null,
    val hasAttemptedGenius: Boolean = false
)

object CachedSongCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class LyricWordDto(
        val text: String,
        val startTimeMs: Long,
        val durationMs: Long
    )

    @Serializable
    private data class BilingualLyricLineDto(
        val lineNumber: Int,
        val original: String,
        val translation: String,
        val isStanzaBreak: Boolean = false,
        val startTimeMs: Long? = null,
        val endTimeMs: Long? = null,
        val words: List<LyricWordDto> = emptyList()
    )

    @Serializable
    private data class LyricAnnotationDto(
        val id: Long = 0,
        val trackId: Long,
        val lyricFragment: String,
        val explanationText: String,
        val authorName: String? = null,
        val authorAvatarUrl: String? = null,
        val isVerified: Boolean = false,
        val votesTotal: Int = 0,
        val imageUrlsJson: String? = null,
        val source: String = "GENIUS",
        val geniusSongId: Long? = null,
        val geniusUrl: String? = null,
        val explanationTranslation: String? = null,
        val lyricTranslation: String? = null
    )

    @Serializable
    private data class AnnotatedLineEntryDto(
        val lineIndex: Int,
        val annotation: LyricAnnotationDto
    )

    @Serializable
    private data class SongStoryDto(
        val trackId: Long,
        val geniusSongId: Long? = null,
        val title: String,
        val artist: String,
        val descriptionPlain: String,
        val descriptionTranslation: String? = null,
        val releaseDate: String? = null,
        val headerImageUrl: String? = null,
        val songArtImageUrl: String? = null,
        val producerCredits: String? = null,
        val songUrl: String? = null,
        val source: String = "GENIUS"
    )

    fun encodeLyrics(lyrics: List<BilingualLyricLine>): String {
        val dtos = lyrics.map { line ->
            BilingualLyricLineDto(
                lineNumber = line.lineNumber,
                original = line.original,
                translation = line.translation,
                isStanzaBreak = line.isStanzaBreak,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                words = line.words.map { w ->
                    LyricWordDto(text = w.text, startTimeMs = w.startTimeMs, durationMs = w.durationMs)
                }
            )
        }
        return json.encodeToString(ListSerializer(BilingualLyricLineDto.serializer()), dtos)
    }

    fun decodeLyrics(rawJson: String): List<BilingualLyricLine> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(BilingualLyricLineDto.serializer()), rawJson)
                .map { dto ->
                    BilingualLyricLine(
                        lineNumber = dto.lineNumber,
                        original = dto.original,
                        translation = dto.translation,
                        isStanzaBreak = dto.isStanzaBreak,
                        startTimeMs = dto.startTimeMs,
                        endTimeMs = dto.endTimeMs,
                        words = dto.words.map { w ->
                            LyricWord(text = w.text, startTimeMs = w.startTimeMs, durationMs = w.durationMs)
                        }
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun encodeAnnotations(annotatedLines: Map<Int, LyricAnnotationEntity>): String {
        val entries = annotatedLines.entries
            .sortedBy { it.key }
            .map { (lineIdx, entity) ->
                AnnotatedLineEntryDto(
                    lineIndex = lineIdx,
                    annotation = LyricAnnotationDto(
                        id = entity.id,
                        trackId = entity.trackId,
                        lyricFragment = entity.lyricFragment,
                        explanationText = entity.explanationText,
                        authorName = entity.authorName,
                        authorAvatarUrl = entity.authorAvatarUrl,
                        isVerified = entity.isVerified,
                        votesTotal = entity.votesTotal,
                        imageUrlsJson = entity.imageUrlsJson,
                        source = entity.source,
                        geniusSongId = entity.geniusSongId,
                        geniusUrl = entity.geniusUrl,
                        explanationTranslation = entity.explanationTranslation,
                        lyricTranslation = entity.lyricTranslation
                    )
                )
            }
        return json.encodeToString(ListSerializer(AnnotatedLineEntryDto.serializer()), entries)
    }

    fun decodeAnnotations(rawJson: String): Map<Int, LyricAnnotationEntity> {
        if (rawJson.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(ListSerializer(AnnotatedLineEntryDto.serializer()), rawJson)
                .associate { entry ->
                    val a = entry.annotation
                    entry.lineIndex to LyricAnnotationEntity(
                        id = a.id,
                        trackId = a.trackId,
                        lyricFragment = a.lyricFragment,
                        explanationText = a.explanationText,
                        authorName = a.authorName,
                        authorAvatarUrl = a.authorAvatarUrl,
                        isVerified = a.isVerified,
                        votesTotal = a.votesTotal,
                        imageUrlsJson = a.imageUrlsJson,
                        source = a.source,
                        geniusSongId = a.geniusSongId,
                        geniusUrl = a.geniusUrl,
                        explanationTranslation = a.explanationTranslation,
                        lyricTranslation = a.lyricTranslation
                    )
                }
        }.getOrDefault(emptyMap())
    }

    fun encodeSongStory(story: SongStoryEntity?): String? {
        if (story == null) return null
        val dto = SongStoryDto(
            trackId = story.trackId,
            geniusSongId = story.geniusSongId,
            title = story.title,
            artist = story.artist,
            descriptionPlain = story.descriptionPlain,
            descriptionTranslation = story.descriptionTranslation,
            releaseDate = story.releaseDate,
            headerImageUrl = story.headerImageUrl,
            songArtImageUrl = story.songArtImageUrl,
            producerCredits = story.producerCredits,
            songUrl = story.songUrl,
            source = story.source
        )
        return json.encodeToString(dto)
    }

    fun decodeSongStory(rawJson: String?): SongStoryEntity? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val dto = json.decodeFromString(SongStoryDto.serializer(), rawJson)
            SongStoryEntity(
                trackId = dto.trackId,
                geniusSongId = dto.geniusSongId,
                title = dto.title,
                artist = dto.artist,
                descriptionPlain = dto.descriptionPlain,
                descriptionTranslation = dto.descriptionTranslation,
                releaseDate = dto.releaseDate,
                headerImageUrl = dto.headerImageUrl,
                songArtImageUrl = dto.songArtImageUrl,
                producerCredits = dto.producerCredits,
                songUrl = dto.songUrl,
                source = dto.source
            )
        }.getOrNull()
    }

    fun toEntity(
        cacheKey: String,
        lyrics: List<BilingualLyricLine>,
        annotatedLines: Map<Int, LyricAnnotationEntity>,
        songStory: SongStoryEntity?,
        geniusNotice: String?,
        hasAttemptedGenius: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    ): CachedSongEntity {
        return CachedSongEntity(
            cacheKey = cacheKey,
            lyricsJson = encodeLyrics(lyrics),
            songStoryJson = encodeSongStory(songStory),
            annotationsJson = encodeAnnotations(annotatedLines),
            geniusNotice = geniusNotice,
            hasAttemptedGenius = hasAttemptedGenius,
            updatedAt = updatedAt
        )
    }

    fun fromEntity(entity: CachedSongEntity): CachedSongPayload {
        return CachedSongPayload(
            lyrics = decodeLyrics(entity.lyricsJson),
            annotatedLines = decodeAnnotations(entity.annotationsJson),
            songStory = decodeSongStory(entity.songStoryJson),
            geniusNotice = entity.geniusNotice,
            hasAttemptedGenius = entity.hasAttemptedGenius
        )
    }
}
