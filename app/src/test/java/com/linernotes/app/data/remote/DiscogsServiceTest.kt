package com.linernotes.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscogsServiceTest {

    @Test
    fun testParseDurationToMs_validFormats() {
        assertEquals(225000L, DiscogsService.parseDurationToMs("3:45"))
        assertEquals(30000L, DiscogsService.parseDurationToMs("0:30"))
        assertEquals(3735000L, DiscogsService.parseDurationToMs("1:02:15"))
        assertEquals(0L, DiscogsService.parseDurationToMs("0:00"))
    }

    @Test
    fun testParseDurationToMs_invalidOrEmpty() {
        assertNull(DiscogsService.parseDurationToMs(null))
        assertNull(DiscogsService.parseDurationToMs(""))
        assertNull(DiscogsService.parseDurationToMs("   "))
        assertNull(DiscogsService.parseDurationToMs("unknown"))
        assertNull(DiscogsService.parseDurationToMs("1:2:3:4"))
    }

    @Test
    fun testDeterminePrimaryFormat_variousPackaging() {
        assertEquals("SHM-CD", DiscogsService.determinePrimaryFormat(listOf("CD", "Album", "SHM-CD")))
        assertEquals("SACD", DiscogsService.determinePrimaryFormat(listOf("SACD", "Hybrid")))
        assertEquals("Mini-LP 纸套 CD", DiscogsService.determinePrimaryFormat(listOf("CD", "Mini-LP", "Paper Sleeve")))
        assertEquals("Digipak CD", DiscogsService.determinePrimaryFormat(listOf("CD", "Digipak")))
        assertEquals("XRCD", DiscogsService.determinePrimaryFormat(listOf("CD", "XRCD24")))
        assertEquals("BSCD2", DiscogsService.determinePrimaryFormat(listOf("CD", "Blu-spec CD2")))
        assertEquals("HDCD", DiscogsService.determinePrimaryFormat(listOf("CD", "HDCD")))
        assertEquals("Box Set 盒装 CD", DiscogsService.determinePrimaryFormat(listOf("CD", "Box Set")))
        assertEquals("标准 CD", DiscogsService.determinePrimaryFormat(listOf("CD", "Album")))
        assertEquals("标准 CD", DiscogsService.determinePrimaryFormat(emptyList()))
    }

    @Test
    fun testBlankQueriesReturnEmptyList() = runBlocking {
        assertTrue(DiscogsService.searchReleasesByAlbumAndArtist("", "").isEmpty())
        assertTrue(DiscogsService.searchReleasesByAlbumAndArtist("   ", "Artist").isEmpty())
        assertTrue(DiscogsService.searchReleases("").isEmpty())
        assertTrue(DiscogsService.searchReleases("   ").isEmpty())
        assertTrue(DiscogsService.searchByBarcode("").isEmpty())
        assertTrue(DiscogsService.searchByBarcode("---").isEmpty())
    }

    @Test
    fun testDiscogsReleaseDetailDefaults() {
        val detail = DiscogsReleaseDetail(
            id = 123L,
            title = "Test Album",
            artist = "Test Artist",
            year = "2024",
            country = "Japan",
            releasedDate = "2024-01-01",
            label = "Sony Music",
            catalogNumber = "SICX-100",
            barcode = "4547366000000",
            formats = listOf("CD", "SHM-CD"),
            mediaType = "SHM-CD",
            notes = "Test notes",
            coverUrl = "http://example.com/cover.jpg",
            bookletImageUrls = listOf("http://example.com/scan1.jpg"),
            tracklist = emptyList(),
            credits = emptyList()
        )
        assertTrue(detail.genres.isEmpty())
        assertTrue(detail.styles.isEmpty())
        assertTrue(detail.companies.isEmpty())
        assertNull(detail.rating)
        assertNull(detail.haveCount)
    }
}
