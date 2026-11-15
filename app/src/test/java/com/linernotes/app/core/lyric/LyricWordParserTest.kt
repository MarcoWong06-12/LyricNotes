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

    @Test
    fun testCleanInlineTimestamps_stripsKrcAndHeaderTags() {
        val krc = "[10000,2000]<0,400,0>Hello <400,600,0>World"
        assertEquals("Hello World", LyricWordParser.cleanInlineTimestamps(krc))

        val qrc = "Hel(10000,500)lo(10500,500)"
        assertEquals("Hello", LyricWordParser.cleanInlineTimestamps(qrc))
    }

    @Test
    fun testQrcTagAfterWordParsingAndClamping() {
        val qrcLine = "Hel(10000,500)lo (10500,500)World(11000,1500)"
        val words = LyricWordParser.parseLineWords(qrcLine, 10000L, 12000L)

        assertEquals(3, words.size)
        assertEquals("Hel", words[0].text)
        assertEquals(10000L, words[0].startTimeMs)
        assertEquals(500L, words[0].durationMs)

        assertEquals("lo ", words[1].text)
        assertEquals(10500L, words[1].startTimeMs)
        assertEquals(500L, words[1].durationMs)

        // 第三个词原本写到 11000+1500=12500，必须被严格钳制在行尾 12000 内
        assertEquals("World", words[2].text)
        assertEquals(11000L, words[2].startTimeMs)
        assertEquals(1000L, words[2].durationMs)
        assertTrue(words.all { it.startTimeMs >= 10000L && it.startTimeMs + it.durationMs <= 12000L })
    }

    @Test
    fun testKrcRelativeAndAbsoluteAngleTags() {
        val krcRelative = "<0,400,0>故<400,600,0>事<1000,1500,0>结尾"
        val words = LyricWordParser.parseLineWords(krcRelative, 10000L, 12000L)

        assertEquals(3, words.size)
        assertEquals("故", words[0].text)
        assertEquals(10000L, words[0].startTimeMs)
        assertEquals(400L, words[0].durationMs)

        assertEquals("事", words[1].text)
        assertEquals(10400L, words[1].startTimeMs)
        assertEquals(600L, words[1].durationMs)

        // 相对偏移 1000+10000=11000，原始持续 1500 超出行尾 12000，须被钳制为 1000ms
        assertEquals("结尾", words[2].text)
        assertEquals(11000L, words[2].startTimeMs)
        assertEquals(1000L, words[2].durationMs)
    }

    @Test
    fun testStandardLrcInterpolationStrictlyClampedWithinShortGap() {
        // 行跨度仅 120ms，但有 6 个词，每个词插值绝对不得溢出 [1000L, 1120L]
        val words = LyricWordParser.parseLineWords("一二三四五六", 1000L, 1120L)
        assertEquals(6, words.size)
        assertEquals(1000L, words.first().startTimeMs)
        for (word in words) {
            assertTrue(word.startTimeMs >= 1000L)
            assertTrue(word.startTimeMs + word.durationMs <= 1120L)
            assertTrue(word.durationMs > 0L)
        }
    }
}
