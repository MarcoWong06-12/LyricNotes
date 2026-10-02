package com.linernotes.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationServiceTest {

    @Test
    fun testEmptyInputHandling() = runBlocking {
        val service = TranslationService()
        val result = service.translateTrack("", "")
        assertNull(result.translatedTitle)
        assertEquals("", result.translatedLyrics)
    }

    @Test
    fun testBlankTitleHandling() = runBlocking {
        val service = TranslationService()
        val result = service.translateTrack("   ", "")
        assertNull(result.translatedTitle)
        assertEquals("", result.translatedLyrics)
    }
}
