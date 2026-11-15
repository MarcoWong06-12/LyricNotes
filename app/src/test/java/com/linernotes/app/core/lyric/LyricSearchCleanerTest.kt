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
        assertEquals("LOVE.", LyricSearchCleaner.cleanTrackTitle("LOVE. FEAT. ZACARI."))
        assertEquals("LOYALTY.", LyricSearchCleaner.cleanTrackTitle("LOYALTY. FEAT. RIHANNA."))
        assertEquals("XXX.", LyricSearchCleaner.cleanTrackTitle("XXX. FEAT. U2."))
        assertEquals("Shape of You", LyricSearchCleaner.cleanTrackTitle("Shape of You feat. Khalid"))
        assertEquals("Dance With Me", LyricSearchCleaner.cleanTrackTitle("Dance With Me"))
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

    @Test
    fun testKoreanHangulCyrillicAndDiacriticNormalization() {
        // 韩文 Hangul 不得被丢弃，且 NFD/NFC 组合韩文应一致归一化
        assertEquals("밤편지", LyricSearchCleaner.normalizeForMatching("밤편지 (Through the Night)"))
        assertEquals("아이유", LyricSearchCleaner.normalizeForMatching("아이유 (IU)".substringBefore(" (")))
        assertEquals(
            LyricSearchCleaner.normalizeForMatching("밤편지"),
            LyricSearchCleaner.normalizeForMatching("\u1107\u1161\u11B7\u1111\u1167\u11AB\u110C\u1175")
        )

        // 俄语 Cyrillic 保留（包括 й 与 ё）
        assertEquals("группакрови", LyricSearchCleaner.normalizeForMatching("Группа крови"))
        assertEquals("кино", LyricSearchCleaner.normalizeForMatching("Кино"))

        // 拉丁音调符号剥离但保留基础字母，同时不破坏韩日文字母本身
        assertEquals("beyonce", LyricSearchCleaner.normalizeForMatching("Beyoncé"))
        assertEquals("canciondeamor", LyricSearchCleaner.normalizeForMatching("Canción de amor"))
        assertEquals("rosalia", LyricSearchCleaner.normalizeForMatching("ROSALÍA"))
    }

    @Test
    fun testKoreanHangulAndCyrillicCandidateScoring_doesNotReturnMinus100() {
        val hangulExact = LyricSearchCleaner.scoreCandidateMatch(
            candidateTitle = "밤편지",
            candidateArtist = "아이유",
            targetTitle = "밤편지",
            targetArtist = "아이유"
        )
        assertTrue("Expected high positive score for Hangul match, got $hangulExact", hangulExact >= 80)

        val hangulMismatch = LyricSearchCleaner.scoreCandidateMatch(
            candidateTitle = "좋은 날",
            candidateArtist = "아이유",
            targetTitle = "밤편지",
            targetArtist = "아이유"
        )
        assertEquals(-100, hangulMismatch)

        val cyrillicExact = LyricSearchCleaner.scoreCandidateMatch(
            candidateTitle = "Группа крови",
            candidateArtist = "Кино",
            targetTitle = "Группа крови",
            targetArtist = "Кино"
        )
        assertTrue("Expected high positive score for Cyrillic match, got $cyrillicExact", cyrillicExact >= 80)

        val diacriticScore = LyricSearchCleaner.scoreCandidateMatch(
            candidateTitle = "Canción de Amor",
            candidateArtist = "Beyoncé",
            targetTitle = "Cancion de Amor",
            targetArtist = "Beyonce"
        )
        assertTrue("Expected high positive score across diacritics, got $diacriticScore", diacriticScore >= 80)
    }

    @Test
    fun testExtractLyricSungFingerprint_preservesHangulCyrillicAndNormalizedLatin() {
        val mixedLrc = """
            [00:10.00] (10000,300,0)이 (10300,400,0)밤 (10700,500,0)그날의 반딧불을
            [00:15.00] Группа крови на рукаве
            [00:20.00] Canción para Beyoncé
        """.trimIndent()

        val fp = LyricSearchCleaner.extractLyricSungFingerprint(mixedLrc)
        assertEquals(3, fp.size)
        assertTrue(fp[0].contains("이밤그날의반딧불을"))
        assertTrue(fp[1].contains("группакровинарукаве"))
        assertEquals("cancion para beyonce", fp[2])
    }
}
