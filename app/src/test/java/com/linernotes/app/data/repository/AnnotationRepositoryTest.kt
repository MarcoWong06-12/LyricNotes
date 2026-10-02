package com.linernotes.app.data.repository

import com.linernotes.app.data.local.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationRepositoryTest {

    private fun createTrack(trackNumber: Int, title: String): TrackEntity {
        return TrackEntity(
            id = trackNumber.toLong(),
            albumId = "album_test",
            trackNumber = trackNumber,
            title = title
        )
    }

    @Test
    fun testPrioritizeTracksAround_middleTrack() {
        // 模拟 6 首歌曲专辑，用户正在听第 3 首歌 (index = 2, Track 3)
        val tracks = (1..6).map { createTrack(it, "Track $it") }

        val prioritized = AnnotationRepository.prioritizeTracksAround(tracks, currentIndex = 2)

        // 排除当前曲目 (Track 3)
        assertEquals(5, prioritized.size)
        assertTrue(prioritized.none { it.trackNumber == 3 })

        // 优先级顺序应为：
        // 距离 1：Track 4 (向后) 优于 Track 2 (向前)
        // 距离 2：Track 5 (向后) 优于 Track 1 (向前)
        // 距离 3：Track 6 (向后)
        assertEquals(4, prioritized[0].trackNumber)
        assertEquals(2, prioritized[1].trackNumber)
        assertEquals(5, prioritized[2].trackNumber)
        assertEquals(1, prioritized[3].trackNumber)
        assertEquals(6, prioritized[4].trackNumber)
    }

    @Test
    fun testPrioritizeTracksAround_firstTrack() {
        // 用户正在听第 1 首歌 (index = 0, Track 1)
        val tracks = (1..5).map { createTrack(it, "Track $it") }

        val prioritized = AnnotationRepository.prioritizeTracksAround(tracks, currentIndex = 0)

        assertEquals(4, prioritized.size)
        assertTrue(prioritized.none { it.trackNumber == 1 })

        // 顺序应为向后依次预热：Track 2, Track 3, Track 4, Track 5
        assertEquals(listOf(2, 3, 4, 5), prioritized.map { it.trackNumber })
    }

    @Test
    fun testPrioritizeTracksAround_lastTrack() {
        // 用户正在听最后一首歌 (index = 4, Track 5)
        val tracks = (1..5).map { createTrack(it, "Track $it") }

        val prioritized = AnnotationRepository.prioritizeTracksAround(tracks, currentIndex = 4)

        assertEquals(4, prioritized.size)
        assertTrue(prioritized.none { it.trackNumber == 5 })

        // 顺序应为向前依次预热：Track 4, Track 3, Track 2, Track 1
        assertEquals(listOf(4, 3, 2, 1), prioritized.map { it.trackNumber })
    }

    @Test
    fun testPrioritizeTracksAround_emptyOrSingle() {
        val emptyTracks = emptyList<TrackEntity>()
        assertTrue(AnnotationRepository.prioritizeTracksAround(emptyTracks, 0).isEmpty())

        val singleTrack = listOf(createTrack(1, "Only Track"))
        assertEquals(singleTrack, AnnotationRepository.prioritizeTracksAround(singleTrack, 0))
    }
}
