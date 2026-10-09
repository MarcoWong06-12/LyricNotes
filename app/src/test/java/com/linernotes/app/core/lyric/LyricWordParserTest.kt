package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricWordParserTest {

    @Test
    fun testCleanInlineTimestamps() {
        val yrc = "(1000,500,0)Hello (1500,600,0)World"
        assertEquals("Hello World", LyricWordParser.cleanInlineTimestamps(yrc))

        val enhanced = "<00:01.00>Hello <00:01.50>World"
        assertEquals("Hello World", LyricWordParser.cleanInlineTimestamps(enhanced))

        val standardWithBracket = "[01:23.45]Hello World"
        assertEquals("Hello World", LyricWordParser.cleanInlineTimestamps(standardWithBracket))
    }

    @Test
    fun testParseYrcParenthesesTimestamps() {
        val line = "(1000,400,0)One (1400,500,0)Two (1900,600,0)Three"
        val words = LyricWordParser.parseLineWords(line, 1000L, 2500L)

        assertEquals(3, words.size)
        assertEquals("One ", words[0].text)
        assertEquals(1000L, words[0].startTimeMs)
        assertEquals(400L, words[0].durationMs)

        assertEquals("Two ", words[1].text)
        assertEquals(1400L, words[1].startTimeMs)
        assertEquals(500L, words[1].durationMs)

        assertEquals("Three", words[2].text)
        assertEquals(1900L, words[2].startTimeMs)
        assertEquals(600L, words[2].durationMs)
    }

    @Test
    fun testParseEnhancedLrcAngledTimestamps() {
        val line = "<00:01.00>Apple <00:01.50>Music <00:02.20>Lyrics"
        val words = LyricWordParser.parseLineWords(line, 1000L, 3000L)

        assertEquals(3, words.size)
        assertEquals("Apple ", words[0].text)
        assertEquals(1000L, words[0].startTimeMs)
        assertEquals(500L, words[0].durationMs)

        assertEquals("Music ", words[1].text)
        assertEquals(1500L, words[1].startTimeMs)
        assertEquals(700L, words[1].durationMs)

        assertEquals("Lyrics", words[2].text)
        assertEquals(2200L, words[2].startTimeMs)
        assertEquals(800L, words[2].durationMs) // 3000 - 2200 = 800ms
    }

    @Test
    fun testStandardLrcCjkTokenDistribution() {
        val line = "故事的小黄花"
        val words = LyricWordParser.parseLineWords(line, 5000L, 8000L)

        assertEquals(6, words.size)
        assertEquals("故", words[0].text)
        assertEquals("事", words[1].text)
        assertEquals("的", words[2].text)
        assertEquals("小", words[3].text)
        assertEquals("黄", words[4].text)
        assertEquals("花", words[5].text)

        // 验证时间戳单调递增
        for (i in 0 until words.size - 1) {
            assertTrue(words[i].startTimeMs < words[i + 1].startTimeMs)
            assertTrue(words[i].durationMs > 0)
        }
        assertEquals(5000L, words[0].startTimeMs)
    }

    @Test
    fun testStandardLrcWesternWordDistribution() {
        val line = "I know how you work"
        val words = LyricWordParser.parseLineWords(line, 2000L, 4000L)

        assertEquals(5, words.size)
        assertEquals("I ", words[0].text)
        assertEquals("know ", words[1].text)
        assertEquals("how ", words[2].text)
        assertEquals("you ", words[3].text)
        assertEquals("work", words[4].text)

        // 验证每个词的时长和时间戳单调递增
        for (i in 0 until words.size - 1) {
            assertTrue(words[i].startTimeMs < words[i + 1].startTimeMs)
            assertTrue(words[i].durationMs >= 60L)
        }
    }

    @Test
    fun testEdgeCasesEmptyAndZeroDuration() {
        assertTrue(LyricWordParser.parseLineWords("", 1000L, 2000L).isEmpty())
        assertTrue(LyricWordParser.parseLineWords("   ", 1000L, 2000L).isEmpty())
        assertTrue(LyricWordParser.parseLineWords("Hello", null, 2000L).isEmpty())

        // 持续时间为0或负数时自动防御
        val words = LyricWordParser.parseLineWords("Hello World", 5000L, 5000L)
        assertTrue(words.isNotEmpty())
        for (word in words) {
            assertTrue(word.durationMs >= 60L)
        }
    }
}
