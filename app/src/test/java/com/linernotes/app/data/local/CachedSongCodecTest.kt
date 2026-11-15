package com.linernotes.app.data.local

import com.linernotes.app.data.local.dao.CachedSongDao
import com.linernotes.app.data.local.entity.CachedSongEntity
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedSongCodecTest {

    private class FakeCachedSongDao : CachedSongDao {
        private val store = linkedMapOf<String, CachedSongEntity>()

        override suspend fun getByKey(cacheKey: String): CachedSongEntity? = store[cacheKey]

        override suspend fun upsert(entity: CachedSongEntity) {
            store[entity.cacheKey] = entity
        }

        override suspend fun deleteByKey(cacheKey: String) {
            store.remove(cacheKey)
        }

        override suspend fun deleteOlderThan(cutoffMs: Long) {
            val keysToRemove = store.values.filter { it.updatedAt < cutoffMs }.map { it.cacheKey }
            keysToRemove.forEach { store.remove(it) }
        }

        override suspend fun count(): Int = store.size

        override suspend fun trimToMaxEntries(maxEntries: Int) {
            if (store.size <= maxEntries) return
            val keepKeys = store.values
                .sortedByDescending { it.updatedAt }
                .take(maxEntries)
                .map { it.cacheKey }
                .toSet()
            store.keys.retainAll(keepKeys)
        }
    }

    @Test
    fun roundtripSerialization_preservesLyricsWordsStoryAndAnnotations() = runBlocking {
        val dao = FakeCachedSongDao()
        val cacheKey = "kendrick lamar - dna.|auto_first|zh-CN|false|true|auto"

        val lyrics = listOf(
            BilingualLyricLine(
                lineNumber = 1,
                original = "I got, I got, I got, I got",
                translation = "我拥有 我拥有 我拥有 我拥有",
                isStanzaBreak = false,
                startTimeMs = 1200L,
                endTimeMs = 3400L,
                words = listOf(
                    LyricWord("I ", 1200L, 200L),
                    LyricWord("got,", 1400L, 300L)
                )
            ),
            BilingualLyricLine(
                lineNumber = 2,
                original = "",
                translation = "",
                isStanzaBreak = true
            )
        )

        val story = SongStoryEntity(
            trackId = 3047141L,
            geniusSongId = 3047141L,
            title = "DNA.",
            artist = "Kendrick Lamar",
            descriptionPlain = "Track 2 on DAMN.",
            descriptionTranslation = "《DAMN.》专辑的第二首曲目。",
            releaseDate = "April 14, 2017",
            headerImageUrl = "https://images.genius.com/header.jpg",
            songArtImageUrl = "https://images.genius.com/art.jpg",
            producerCredits = "Mike WiLL Made-It",
            songUrl = "https://genius.com/Kendrick-lamar-dna-lyrics",
            source = "GENIUS"
        )

        val annotations = mapOf(
            0 to LyricAnnotationEntity(
                id = 10L,
                trackId = 3047141L,
                lyricFragment = "I got, I got, I got, I got",
                explanationText = "Loyalty, got royalty inside my DNA.",
                authorName = "RapGenius",
                authorAvatarUrl = "https://images.genius.com/avatar.jpg",
                isVerified = true,
                votesTotal = 128,
                imageUrlsJson = "[\"https://images.genius.com/inline.jpg\"]",
                source = "GENIUS",
                geniusSongId = 3047141L,
                geniusUrl = "https://genius.com/111",
                explanationTranslation = "忠诚与皇室血脉刻在我的基因里。",
                lyricTranslation = "我拥有，我拥有"
            )
        )

        val entity = CachedSongCodec.toEntity(
            cacheKey = cacheKey,
            lyrics = lyrics,
            annotatedLines = annotations,
            songStory = story,
            geniusNotice = null,
            hasAttemptedGenius = true,
            updatedAt = 1_700_000_000_000L
        )
        dao.upsert(entity)

        val loaded = dao.getByKey(cacheKey)
        assertNotNull(loaded)
        val payload = CachedSongCodec.fromEntity(loaded!!)

        assertEquals(lyrics, payload.lyrics)
        assertEquals(story, payload.songStory)
        assertEquals(annotations, payload.annotatedLines)
        assertNull(payload.geniusNotice)
        assertTrue(payload.hasAttemptedGenius)
    }

    @Test
    fun corruptJson_decodesSafelyWithoutThrowing() {
        assertEquals(emptyList<BilingualLyricLine>(), CachedSongCodec.decodeLyrics("{not valid json"))
        assertEquals(emptyMap<Int, LyricAnnotationEntity>(), CachedSongCodec.decodeAnnotations("[bad"))
        assertNull(CachedSongCodec.decodeSongStory("corrupt"))
        assertNull(CachedSongCodec.decodeSongStory(null))
        assertNull(CachedSongCodec.decodeSongStory("   "))
    }

    @Test
    fun fakeDao_trimToMaxEntries_retainsMostRecent() = runBlocking {
        val dao = FakeCachedSongDao()
        dao.upsert(CachedSongEntity(cacheKey = "k1", lyricsJson = "[]", updatedAt = 100L))
        dao.upsert(CachedSongEntity(cacheKey = "k2", lyricsJson = "[]", updatedAt = 300L))
        dao.upsert(CachedSongEntity(cacheKey = "k3", lyricsJson = "[]", updatedAt = 200L))

        dao.trimToMaxEntries(2)

        assertEquals(2, dao.count())
        assertNull(dao.getByKey("k1"))
        assertNotNull(dao.getByKey("k2"))
        assertNotNull(dao.getByKey("k3"))
    }
}
