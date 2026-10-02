package com.linernotes.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeniusServiceTest {

    @Test
    fun testDataModelsInstantiation() {
        val searchResult = GeniusSongSearchResult(
            id = 452L,
            title = "Ms. Jackson",
            fullTitle = "Ms. Jackson by OutKast",
            artist = "OutKast",
            thumbUrl = "https://images.genius.com/thumb.jpg",
            coverUrl = "https://images.genius.com/cover.jpg",
            url = "https://genius.com/Outkast-ms-jackson-lyrics"
        )
        assertEquals(452L, searchResult.id)
        assertEquals("Ms. Jackson", searchResult.title)

        val detail = GeniusSongDetail(
            id = 452L,
            title = "Ms. Jackson",
            artist = "OutKast",
            descriptionPlain = "The second single from Stankonia...",
            releaseDate = "October 24, 2000",
            headerImageUrl = "https://images.genius.com/header.jpg",
            songArtImageUrl = "https://images.genius.com/art.jpg",
            producerCredits = "Earthtone III, Organized Noize",
            songUrl = "https://genius.com/Outkast-ms-jackson-lyrics"
        )
        assertEquals("October 24, 2000", detail.releaseDate)
        assertNotNull(detail.producerCredits)

        val annotation = GeniusAnnotationItem(
            id = 12345L,
            bodyPlain = "Andre 3000 explains the backstory...",
            bodyHtml = "<p>Andre 3000 explains the backstory...<img src=\"https://images.genius.com/pic.jpg\"></p>",
            verified = true,
            authorName = "Andre 3000",
            authorAvatarUrl = "https://images.genius.com/avatar.jpg",
            votesTotal = 42,
            url = "https://genius.com/12345",
            imageUrls = listOf("https://images.genius.com/pic.jpg")
        )
        assertEquals(true, annotation.verified)
        assertEquals(1, annotation.imageUrls.size)
        assertEquals("Andre 3000", annotation.authorName)
    }

    @Test
    fun testSanitizeTitle() {
        // 音轨号剥离
        assertEquals("LOYALTY.", GeniusService.sanitizeTitle("01. LOYALTY."))
        assertEquals("DNA.", GeniusService.sanitizeTitle("1 - DNA."))
        assertEquals("HUMBLE.", GeniusService.sanitizeTitle("02 HUMBLE."))
        assertEquals("Intro", GeniusService.sanitizeTitle("Track 01 - Intro"))

        // 以数字开头的歌曲名称必须完整保留，不能被误伤
        assertEquals("21 Guns", GeniusService.sanitizeTitle("21 Guns"))
        assertEquals("7 Rings", GeniusService.sanitizeTitle("7 Rings"))
        assertEquals("1999", GeniusService.sanitizeTitle("1999"))
        assertEquals("505", GeniusService.sanitizeTitle("505"))
        assertEquals("24K Magic", GeniusService.sanitizeTitle("24K Magic"))
    }

    @Test
    fun testUnescapeHtml() {
        assertEquals("I'm so sorry", GeniusService.unescapeHtml("I&#39;m so sorry"))
        assertEquals("you're", GeniusService.unescapeHtml("you&rsquo;re"))
        assertEquals("\"Hello\"", GeniusService.unescapeHtml("&quot;Hello&quot;"))
        assertEquals("Rock & Roll", GeniusService.unescapeHtml("Rock &amp; Roll"))
    }

    @Test
    fun testIsTranslationSpam() {
        // 典型翻译垃圾条目（应被拦截）
        assertEquals(true, GeniusService.isTranslationSpam("Lilac Wine (Traducción al Español)", "Lilac Wine (Traducción al Español) by Genius Traducciones al Español", "Genius Traducciones al Español"))
        assertEquals(true, GeniusService.isTranslationSpam("Lilac Wine", "Lilac Wine by Genius English Translations", "Genius English Translations"))
        assertEquals(true, GeniusService.isTranslationSpam("晴天 (中文翻译)", "晴天 (中文翻译) by 网友", "周杰伦"))
        assertEquals(true, GeniusService.isTranslationSpam("DNA (Romanized)", "DNA (Romanized) by BTS", "BTS"))

        // 真实正版曲目（绝不误伤）
        assertEquals(false, GeniusService.isTranslationSpam("Lilac Wine", "Lilac Wine by Jeff Buckley", "Jeff Buckley"))
        assertEquals(false, GeniusService.isTranslationSpam("Manchild", "Manchild by Sabrina Carpenter", "Sabrina Carpenter"))
        assertEquals(false, GeniusService.isTranslationSpam("Ms. Jackson", "Ms. Jackson by OutKast", "OutKast"))
        assertEquals(false, GeniusService.isTranslationSpam("505", "505 by Arctic Monkeys", "Arctic Monkeys"))
    }
}
