package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSearchCleanerTest {

    @Test
    fun testTrackNumberPrefixRemoval() {
        assertEquals("First Love", LyricSearchCleaner.cleanTrackTitle("01. First Love"))
        assertEquals("Lemon", LyricSearchCleaner.cleanTrackTitle("02 - Lemon"))
        assertEquals("Pretender", LyricSearchCleaner.cleanTrackTitle("Track 03 - Pretender"))
        assertEquals("Bohemian Rhapsody", LyricSearchCleaner.cleanTrackTitle("A1. Bohemian Rhapsody"))
        assertEquals("Song Title", LyricSearchCleaner.cleanTrackTitle("12 Song Title"))
    }

    @Test
    fun testAudioExtensionRemoval() {
        assertEquals("First Love", LyricSearchCleaner.cleanTrackTitle("First Love.flac"))
        assertEquals("Lemon", LyricSearchCleaner.cleanTrackTitle("Lemon.mp3"))
        assertEquals("Pretender", LyricSearchCleaner.cleanTrackTitle("01. Pretender.wav"))
        assertEquals("Stay", LyricSearchCleaner.cleanTrackTitle("Stay.m4a"))
    }

    @Test
    fun testNoiseTagsRemoval() {
        assertEquals("Lemon", LyricSearchCleaner.cleanTrackTitle("Lemon (Official Music Video)"))
        assertEquals("Shape of You", LyricSearchCleaner.cleanTrackTitle("Shape of You (feat. Khalid)"))
        assertEquals("Shape of You", LyricSearchCleaner.cleanTrackTitle("Shape of You - feat. Khalid"))
        assertEquals("Hotel California", LyricSearchCleaner.cleanTrackTitle("Hotel California (Live at The Forum)"))
        assertEquals("In the End", LyricSearchCleaner.cleanTrackTitle("In the End - 2020 Remaster"))
        assertEquals("残酷な天使のテーゼ", LyricSearchCleaner.cleanTrackTitle("残酷な天使のテーゼ (TV Size)"))
        assertEquals("Subtitle", LyricSearchCleaner.cleanTrackTitle("Subtitle [Official Audio]"))
        assertEquals("前前前世", LyricSearchCleaner.cleanTrackTitle("前前前世 (movie ver.)"))
        assertEquals("Automatic", LyricSearchCleaner.cleanTrackTitle("Automatic (Remastered 2024)"))
    }

    @Test
    fun testFullwidthBracketsNormalization() {
        assertEquals("Lemon", LyricSearchCleaner.cleanTrackTitle("Lemon（Official Video）"))
        assertEquals("Subtitle", LyricSearchCleaner.cleanTrackTitle("Subtitle【MV】"))
    }

    @Test
    fun testArtistCleaningAndPlaceholders() {
        assertEquals("", LyricSearchCleaner.cleanArtist("Various Artists"))
        assertEquals("", LyricSearchCleaner.cleanArtist("群星"))
        assertEquals("", LyricSearchCleaner.cleanArtist("Unknown Artist"))
        assertEquals("", LyricSearchCleaner.cleanArtist("unknown"))
        assertEquals("米津玄師", LyricSearchCleaner.cleanArtist("米津玄師"))
    }

    @Test
    fun testExtractPrimaryArtist() {
        assertEquals("Ed Sheeran", LyricSearchCleaner.extractPrimaryArtist("Ed Sheeran / Khalid"))
        assertEquals("Aimer", LyricSearchCleaner.extractPrimaryArtist("Aimer with chelly"))
        assertEquals("RADWIMPS", LyricSearchCleaner.extractPrimaryArtist("RADWIMPS feat. 十明"))
        assertEquals("YOASOBI", LyricSearchCleaner.extractPrimaryArtist("YOASOBI & Ayase"))
        assertEquals("米津玄師", LyricSearchCleaner.extractPrimaryArtist("米津玄師, 菅田将暉"))
    }

    @Test
    fun testBuildSearchQueries() {
        val queries1 = LyricSearchCleaner.buildSearchQueries("01. First Love.flac", "宇多田ヒカル")
        assertEquals(listOf("First Love 宇多田ヒカル", "First Love"), queries1)

        val queries2 = LyricSearchCleaner.buildSearchQueries("02. Lemon", "Various Artists")
        assertEquals(listOf("Lemon"), queries2)

        val queries3 = LyricSearchCleaner.buildSearchQueries("Shape of You (feat. Khalid)", "Ed Sheeran / Khalid")
        assertTrue(queries3.contains("Shape of You Ed Sheeran"))
        assertTrue(queries3.contains("Shape of You Ed Sheeran / Khalid"))
        assertTrue(queries3.contains("Shape of You"))
    }
}
