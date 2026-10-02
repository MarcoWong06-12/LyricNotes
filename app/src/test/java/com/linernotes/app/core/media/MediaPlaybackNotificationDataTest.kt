package com.linernotes.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackNotificationDataTest {

    @Test
    fun `test default notification data parameters`() {
        val data = MediaPlaybackNotificationData()
        assertEquals("", data.albumId)
        assertEquals("", data.trackTitle)
        assertEquals("", data.artist)
        assertEquals("", data.albumTitle)
        assertEquals(null, data.coverUrl)
        assertFalse(data.isPlaying)
        assertEquals(0L, data.positionMs)
        assertEquals(0L, data.durationMs)
        assertEquals(null, data.activeLyricSnippet)
        assertEquals(null, data.activeAnnotationSnippet)
        assertFalse(data.hasPrevious)
        assertFalse(data.hasNext)
    }

    @Test
    fun `test custom media notification data with annotation snippet`() {
        val data = MediaPlaybackNotificationData(
            albumId = "album-123",
            trackTitle = "N.Y. State of Mind",
            artist = "Nas",
            albumTitle = "Illmatic",
            coverUrl = "https://example.com/cover.jpg",
            isPlaying = true,
            positionMs = 60_000L,
            durationMs = 294_000L,
            activeLyricSnippet = "I never sleep, 'cause sleep is the cousin of death • 我从不沉睡，因为沉睡即死亡的孪生兄弟",
            activeAnnotationSnippet = "Nas 借鉴了《伊利亚特》中修普诺斯与塔纳托斯的典故，隐喻皇后桥街区的危险时刻不容松懈。",
            hasPrevious = false,
            hasNext = true
        )

        assertEquals("album-123", data.albumId)
        assertEquals("N.Y. State of Mind", data.trackTitle)
        assertEquals("Nas", data.artist)
        assertEquals("Illmatic", data.albumTitle)
        assertEquals("https://example.com/cover.jpg", data.coverUrl)
        assertTrue(data.isPlaying)
        assertEquals(60_000L, data.positionMs)
        assertEquals(294_000L, data.durationMs)
        assertTrue(data.activeLyricSnippet!!.contains("cousin of death"))
        assertTrue(data.activeAnnotationSnippet!!.contains("修普诺斯"))
        assertFalse(data.hasPrevious)
        assertTrue(data.hasNext)
    }
}
