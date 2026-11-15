package com.linernotes.app.data.repository

import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.remote.OnlineLyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingCacheAndBindingTest {

    @Test
    fun buildCacheKey_distinguishesTraditionalDeCensorAndManualBindings() {
        val baseKey = NowPlayingRepository.buildCacheKey(
            songKey = "sabrina carpenter - espresso",
            lyricsSource = "auto_first",
            targetLanguage = "zh-CN",
            isTraditionalChinese = false,
            isDeCensorEnabled = true,
            manualBinding = null
        )

        val traditionalKey = NowPlayingRepository.buildCacheKey(
            songKey = "sabrina carpenter - espresso",
            lyricsSource = "auto_first",
            targetLanguage = "zh-CN",
            isTraditionalChinese = true,
            isDeCensorEnabled = true,
            manualBinding = null
        )

        val censoredKey = NowPlayingRepository.buildCacheKey(
            songKey = "sabrina carpenter - espresso",
            lyricsSource = "auto_first",
            targetLanguage = "zh-CN",
            isTraditionalChinese = false,
            isDeCensorEnabled = false,
            manualBinding = null
        )

        val neteaseBoundKey = NowPlayingRepository.buildCacheKey(
            songKey = "sabrina carpenter - espresso",
            lyricsSource = "auto_first",
            targetLanguage = "zh-CN",
            isTraditionalChinese = false,
            isDeCensorEnabled = true,
            manualBinding = AiPreferences.ManualBinding(source = "NETEASE", sourceId = "2148392")
        )

        val kugouBoundKey = NowPlayingRepository.buildCacheKey(
            songKey = "sabrina carpenter - espresso",
            lyricsSource = "auto_first",
            targetLanguage = "zh-CN",
            isTraditionalChinese = false,
            isDeCensorEnabled = true,
            manualBinding = AiPreferences.ManualBinding(source = "KUGOU", sourceId = "hash123")
        )

        val lrclibBoundKey = NowPlayingRepository.buildCacheKey(
            songKey = "sabrina carpenter - espresso",
            lyricsSource = "auto_first",
            targetLanguage = "zh-CN",
            isTraditionalChinese = false,
            isDeCensorEnabled = true,
            manualBinding = AiPreferences.ManualBinding(source = "LRCLIB", sourceId = "998877")
        )

        val allKeys = setOf(
            baseKey,
            traditionalKey,
            censoredKey,
            neteaseBoundKey,
            kugouBoundKey,
            lrclibBoundKey
        )
        assertEquals(6, allKeys.size)
        assertNotEquals(baseKey, traditionalKey)
        assertNotEquals(baseKey, censoredKey)
        assertNotEquals(baseKey, neteaseBoundKey)
    }

    @Test
    fun applyOfficialBilingualIfAllowed_neverMixesExternalTranslationWhenManualBindingActive() {
        val manualOrig = "[00:05.00] Bound version line one\n[00:10.00] Bound version line two"
        val official = OnlineLyricsResult(
            songId = 100L,
            title = "Test",
            artist = "Artist",
            originalLyrics = "[00:04.20] Different split line\n[00:09.10] Another line",
            translatedLyrics = "[00:04.20] 外部源译文一\n[00:09.10] 外部源译文二",
            isBilingual = true
        )

        val (resolvedOrig, resolvedTrans) = NowPlayingRepository.applyOfficialBilingualIfAllowed(
            currentOrig = manualOrig,
            currentTrans = null,
            officialBilingual = official,
            allowReplaceOriginWithOfficial = false
        )

        assertEquals(manualOrig, resolvedOrig)
        assertNull(resolvedTrans)
    }

    @Test
    fun applyOfficialBilingualIfAllowed_replacesAtomicallyWhenAutoAllowedAndTimed() {
        val autoOrig = "[00:05.00] Line one\n[00:10.00] Line two"
        val official = OnlineLyricsResult(
            songId = 100L,
            title = "Test",
            artist = "Artist",
            originalLyrics = "[00:05.10] Official line one\n[00:10.10] Official line two",
            translatedLyrics = "[00:05.10] 官方译文一\n[00:10.10] 官方译文二",
            isBilingual = true
        )

        val (resolvedOrig, resolvedTrans) = NowPlayingRepository.applyOfficialBilingualIfAllowed(
            currentOrig = autoOrig,
            currentTrans = null,
            officialBilingual = official,
            allowReplaceOriginWithOfficial = true
        )

        assertEquals(official.originalLyrics, resolvedOrig)
        assertEquals(official.translatedLyrics, resolvedTrans)
    }

    @Test
    fun applyOfficialBilingualIfAllowed_skipsUntimedOfficialWhenCurrentIsTimed() {
        val timedOrig = "[00:05.00] Timed line one\n[00:10.00] Timed line two"
        val untimedOfficial = OnlineLyricsResult(
            songId = 101L,
            title = "Test",
            artist = "Artist",
            originalLyrics = "Untimed line one\nUntimed line two",
            translatedLyrics = "无时间戳译文一\n无时间戳译文二",
            isBilingual = true
        )

        val (resolvedOrig, resolvedTrans) = NowPlayingRepository.applyOfficialBilingualIfAllowed(
            currentOrig = timedOrig,
            currentTrans = null,
            officialBilingual = untimedOfficial,
            allowReplaceOriginWithOfficial = true
        )

        assertEquals(timedOrig, resolvedOrig)
        assertNull(resolvedTrans)
    }
}
