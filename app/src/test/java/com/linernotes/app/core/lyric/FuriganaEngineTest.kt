package com.linernotes.app.core.lyric

import org.junit.Assert.*
import org.junit.Test

class FuriganaEngineTest {

    @Test
    fun testIsJapanese() {
        assertTrue(FuriganaEngine.isJapanese("君の前前前世から僕は"))
        assertTrue(FuriganaEngine.isJapanese("さくら ひらひら"))
        assertTrue(FuriganaEngine.isJapanese("カタカナ"))
        assertFalse(FuriganaEngine.isJapanese("Hello World! This is English."))
        assertFalse(FuriganaEngine.isJapanese("1234567890"))
        assertFalse(FuriganaEngine.isJapanese("晴天 - 周杰伦 故事的小黄花"))
        assertFalse(FuriganaEngine.isJapanese("我爱你中国"))
    }

    @Test
    fun testKatakanaToHiragana() {
        val katakana = "サクラ"
        val hiragana = FuriganaEngine.katakanaToHiragana(katakana)
        assertEquals("さくら", hiragana)

        val mixed = "アイウエオ"
        assertEquals("あいうえお", FuriganaEngine.katakanaToHiragana(mixed))
    }

    @Test
    fun testHiraganaToRomaji() {
        assertEquals("sakura", FuriganaEngine.hiraganaToRomaji("さくら"))
        assertEquals("sekai", FuriganaEngine.hiraganaToRomaji("せかい"))
        assertEquals("toukyou", FuriganaEngine.hiraganaToRomaji("とうきょう"))
        assertEquals("chotto", FuriganaEngine.hiraganaToRomaji("ちょっと"))
    }

    @Test
    fun testParseInlineReadings() {
        val lyricWithFurigana = "夢(ゆめ)を見(み)た"
        val segments = FuriganaEngine.parseInlineReadings(lyricWithFurigana)
        
        assertEquals(4, segments.size)
        assertEquals("夢", segments[0].text)
        assertEquals("ゆめ", segments[0].reading)
        assertTrue(segments[0].isKanji)

        assertEquals("を", segments[1].text)
        assertNull(segments[1].reading)
        assertFalse(segments[1].isKanji)

        assertEquals("見", segments[2].text)
        assertEquals("み", segments[2].reading)
        assertTrue(segments[2].isKanji)

        assertEquals("た", segments[3].text)
        assertNull(segments[3].reading)
        assertFalse(segments[3].isKanji)
    }

    @Test
    fun testSegmentTextWithCommonKanji() {
        val line = "世界中の愛"
        val segments = FuriganaEngine.segmentText(line)
        
        assertTrue(segments.isNotEmpty())
        val sekai = segments.find { it.text == "世界" }
        assertNotNull(sekai)
        assertEquals("せかい", sekai?.reading)

        val naka = segments.find { it.text == "中" }
        assertNotNull(naka)
        assertEquals("なか", naka?.reading)

        val ai = segments.find { it.text == "愛" }
        assertNotNull(ai)
        assertEquals("あい", ai?.reading)
    }
}
