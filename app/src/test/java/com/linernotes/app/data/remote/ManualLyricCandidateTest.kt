package com.linernotes.app.data.remote

import com.linernotes.app.domain.model.LyricCandidateItem
import com.linernotes.app.domain.model.LyricSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLyricCandidateTest {

    @Test
    fun testCandidateItem_creationAndProperties() {
        val item = LyricCandidateItem(
            source = LyricSource.NETEASE,
            sourceId = "2113762",
            title = "Hit 'Em Up",
            artist = "2Pac",
            album = "Greatest Hits",
            durationMs = 312000L,
            coverUrl = "https://example.com/cover.jpg",
            hasTranslation = true
        )

        assertEquals(LyricSource.NETEASE, item.source)
        assertEquals("网易云音乐", item.source.displayName)
        assertEquals("2113762", item.sourceId)
        assertEquals("Hit 'Em Up", item.title)
        assertTrue(item.hasTranslation)
        assertEquals(312000L, item.durationMs)
    }

    @Test
    fun testCandidateSorting_prioritizesTranslationAndMatchingDuration() {
        val targetDurationMs = 300000L // 5 mins

        val c1 = LyricCandidateItem(
            source = LyricSource.NETEASE,
            sourceId = "1",
            title = "Song A (Wrong Duration, No Trans)",
            artist = "Artist",
            durationMs = 120000L,
            hasTranslation = false
        )
        val c2 = LyricCandidateItem(
            source = LyricSource.QQ_MUSIC,
            sourceId = "2",
            title = "Song B (Matched Duration, No Trans)",
            artist = "Artist",
            durationMs = 301000L, // diff 1s
            hasTranslation = false
        )
        val c3 = LyricCandidateItem(
            source = LyricSource.NETEASE,
            sourceId = "3",
            title = "Song C (Matched Duration + Has Trans)",
            artist = "Artist",
            durationMs = 302000L, // diff 2s
            hasTranslation = true
        )

        val list = mutableListOf(c1, c2, c3)

        // Same comparator used in UnifiedLyricsService
        list.sortWith(
            compareByDescending { item ->
                var weight = 0
                if (item.hasTranslation) weight += 100
                if (targetDurationMs > 0L && item.durationMs > 0L) {
                    val diffSec = Math.abs(targetDurationMs - item.durationMs) / 1000L
                    if (diffSec <= 3L) weight += 200
                    else if (diffSec <= 8L) weight += 120
                    else if (diffSec <= 15L) weight += 60
                }
                weight
            }
        )

        // Best is c3 (300 weight = 200 + 100)
        assertEquals("3", list[0].sourceId)
        // Second is c2 (200 weight)
        assertEquals("2", list[1].sourceId)
        // Last is c1 (0 weight)
        assertEquals("1", list[2].sourceId)
    }

    @Test
    fun testManualBindingSerialization() {
        val source = "NETEASE"
        val sourceId = "2113762"
        val extraKey = "album123"

        val serialized = "$source|$sourceId|$extraKey"
        val parts = serialized.split("|")

        assertEquals(3, parts.size)
        assertEquals("NETEASE", parts[0])
        assertEquals("2113762", parts[1])
        assertEquals("album123", parts[2])
    }
}
