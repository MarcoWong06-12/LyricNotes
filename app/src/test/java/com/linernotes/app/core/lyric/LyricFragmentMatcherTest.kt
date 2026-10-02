package com.linernotes.app.core.lyric

import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricFragmentMatcherTest {

    @Test
    fun testNormalizeText() {
        val raw = "[Verse 1: Andre 3000] Forever? Forever, ever? Forever, ever!"
        val norm = LyricFragmentMatcher.normalizeText(raw)
        assertEquals("forever forever ever forever ever", norm)
    }

    @Test
    fun testDirectMatching_exactAndSubstring() {
        val lines = listOf(
            BilingualLyricLine(1, "Me and my daughter, baby mother", "我和我女儿的妈妈"),
            BilingualLyricLine(2, "Forever? Forever, ever? Forever, ever?", "永远？永远永远？"),
            BilingualLyricLine(3, "Close our eyes and hope to god it goes away", "闭上双眼祈祷这一切都会过去")
        )

        val annotations = listOf(
            LyricAnnotationEntity(
                id = 1,
                trackId = 100L,
                lyricFragment = "Forever? Forever, ever? Forever, ever?",
                explanationText = "Andre 3000 explains the backstory behind the song..."
            )
        )

        val matchMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, annotations)
        assertEquals(1, matchMap.size)
        assertNotNull(matchMap[1]) // line index 1 is the 2nd line
        assertEquals(1L, matchMap[1]?.id)
    }

    @Test
    fun testMultiLineFragmentMatching_anchorsOnlyAtStart() {
        val lines = listOf(
            BilingualLyricLine(1, "I hope we feel like this forever", "希望我们能一直像这样"),
            BilingualLyricLine(2, "Forever, forever ever? Forever ever?", "永远？永远永远？"),
            BilingualLyricLine(3, "Forever never seems that long until you're grown", "直到长大后才发觉永远有多遥远"),
            BilingualLyricLine(4, "And notice that the day-by-day ruler can't be too wrong", "发现岁月的刻度尺从不会出错")
        )

        val fragment = """
            I hope we feel like this forever
            Forever, forever ever? Forever ever?
            Forever never seems that long until you’re grown
            And notice that the day-by-day ruler can’t be too wrong
        """.trimIndent()

        val annotations = listOf(
            LyricAnnotationEntity(
                id = 42,
                trackId = 100L,
                lyricFragment = fragment,
                explanationText = "Andre analyses what forever really means..."
            )
        )

        val matchMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, annotations)
        // 多行段落典故仅锚定在起始行，严禁在后续每一行重复铺撒
        assertEquals(1, matchMap.size)
        assertEquals(42L, matchMap[0]?.id)
        assertNull(matchMap[1])
        assertNull(matchMap[2])
        assertNull(matchMap[3])
    }

    @Test
    fun testChineseFragmentMatching_anchorsOnlyAtStart() {
        val lines = listOf(
            BilingualLyricLine(1, "故事的小黄花 从出生那年就飘着", ""),
            BilingualLyricLine(2, "还要多久 我才能在妳身边？", ""),
            BilingualLyricLine(3, "等到放晴的那天 也许我会比较好一点", "")
        )

        val annotations = listOf(
            LyricAnnotationEntity(
                id = 99,
                trackId = 200L,
                lyricFragment = "还要多久 我才能在妳身边？\n等到放晴的那天 也许我会比较好一点",
                explanationText = "周杰伦借晴天的意象表达青涩与遗憾..."
            )
        )

        val matchMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, annotations)
        assertEquals(1, matchMap.size)
        assertEquals(99L, matchMap[1]?.id)
        assertNull(matchMap[2])
    }

    @Test
    fun testSpecificSingleLineTakesPrecedenceOverPassage() {
        val lines = listOf(
            BilingualLyricLine(1, "Hit me", ""),
            BilingualLyricLine(2, "When the four corners of this cocoon collide", ""),
            BilingualLyricLine(3, "You'll slip through the cracks hoping that you'll survive", ""),
            BilingualLyricLine(4, "Gather your wind, take a deep look inside", "")
        )

        val passageAnnotation = LyricAnnotationEntity(
            id = 10,
            trackId = 1L,
            lyricFragment = "Hit me\nWhen the four corners of this cocoon collide\nYou'll slip through the cracks hoping that you'll survive\nGather your wind, take a deep look inside",
            explanationText = "Passage background on James Brown sample..."
        )

        val specificAnnotation = LyricAnnotationEntity(
            id = 20,
            trackId = 1L,
            lyricFragment = "You'll slip through the cracks hoping that you'll survive",
            explanationText = "Specific note about cracks..."
        )

        val matchMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, listOf(passageAnnotation, specificAnnotation))
        // 行 0 挂载大段落典故
        assertEquals(10L, matchMap[0]?.id)
        // 行 2 挂载单行精准典故
        assertEquals(20L, matchMap[2]?.id)
        // 行 1 与行 3 保持清爽干净，不重复显示行 0 的引言
        assertNull(matchMap[1])
        assertNull(matchMap[3])
    }

    @Test
    fun testEmptyInputReturnsEmptyMap() {
        assertTrue(LyricFragmentMatcher.matchAnnotationsToLines(emptyList(), emptyList()).isEmpty())
        assertTrue(LyricFragmentMatcher.matchAnnotationsToLines(listOf(BilingualLyricLine(1, "test", "")), emptyList()).isEmpty())
    }

    @Test
    fun testHtmlEntitiesMatching() {
        val lines = listOf(
            BilingualLyricLine(1, "Tell me who you're loyal to", "告诉我你对谁忠诚"),
            BilingualLyricLine(2, "Is it love for the streets when the lights get dark?", "当黑夜降临，你对街头依然怀揣热爱吗？")
        )

        val annotations = listOf(
            LyricAnnotationEntity(
                id = 55,
                trackId = 300L,
                lyricFragment = "Tell me who you&#39;re loyal to",
                explanationText = "Kendrick explores the boundaries of loyalty..."
            )
        )

        val matchMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, annotations)
        assertEquals(1, matchMap.size)
        assertEquals(55L, matchMap[0]?.id)
    }

    @Test
    fun testSlidingWindowMatching() {
        val lines = listOf(
            BilingualLyricLine(1, "First line of the verse", ""),
            BilingualLyricLine(2, "Second line connecting here", ""),
            BilingualLyricLine(3, "Third line continuing forward", "")
        )

        val annotations = listOf(
            LyricAnnotationEntity(
                id = 88,
                trackId = 400L,
                lyricFragment = "First line of the verse\nSecond line connecting here\nThird line continuing forward",
                explanationText = "Story spanning multiple lines..."
            )
        )

        val matchMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, annotations)
        assertEquals(1, matchMap.size)
        assertEquals(88L, matchMap[0]?.id)
        assertNull(matchMap[1])
        assertNull(matchMap[2])
    }

    @Test
    fun testDnaScenario_fightomoDoesNotPolluteOtherLines() {
        val lines = listOf(
            BilingualLyricLine(1, "I got, I got, I got, I got—", ""),
            BilingualLyricLine(2, "Loyalty, got royalty inside my DNA", ""),
            BilingualLyricLine(3, "Cocaine quarter piece, got war and peace inside my DNA", ""),
            BilingualLyricLine(4, "I got power, poison, pain, and joy inside my DNA", ""),
            BilingualLyricLine(5, "I got hustle though, ambition flow inside my DNA", ""),
            BilingualLyricLine(6, "I was born like this, since one like this, immaculate conception", ""),
            BilingualLyricLine(7, "I transform like this, perform like this, Was Yeshua new weapon", ""),
            BilingualLyricLine(8, "I don’t contemplate, I meditate, then off your fucking head", ""),
            BilingualLyricLine(9, "This that put-the-kids-to-bed", ""),
            BilingualLyricLine(10, "This that I got, I got, I got, I got—", ""),
            BilingualLyricLine(11, "Realness, I just kill shit 'cause it's in my DNA", ""),
            BilingualLyricLine(12, "I got millions, I got riches buildin' in my DNA", ""),
            BilingualLyricLine(13, "I got dark, I got evil, that rot inside my DNA", ""),
            BilingualLyricLine(14, "I got off, I got trouble, that's inside my DNA", ""),
            BilingualLyricLine(15, "I just win again, then win again, like Wimbledon, I serve", ""),
            BilingualLyricLine(16, "Yeah, that's him again, the sound that engine in is like a bird", ""),
            BilingualLyricLine(17, "You see fireworks and Corvette tire smoke, compliment the vibe and", ""),
            BilingualLyricLine(18, "Tell me who you're loyal to", ""),
            BilingualLyricLine(19, "Is it love for the streets when the lights get dark?", ""),
            BilingualLyricLine(20, "I know how you work, I know just who you are", "")
        )

        val fightomo = LyricAnnotationEntity(
            id = 11666737L,
            trackId = 2L,
            lyricFragment = "I got, I got, I got, I got—\nLoyalty, got royalty inside my DNA",
            explanationText = "Kendrick's mom taught him the value of loyalty...",
            votesTotal = 467
        )

        // 仅存在 Fightomo 单条注释时：绝对只能挂载在第 0 行，绝不可泛滥扩散至第 9、12、15、19 行！
        val singleMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, listOf(fightomo))
        assertEquals(1, singleMap.size)
        assertEquals(11666737L, singleMap[0]?.id)
        assertNull(singleMap[9])
        assertNull(singleMap[12])
        assertNull(singleMap[15])
        assertNull(singleMap[19])

        // 引入各行专属注释时，各司其职，互不覆盖
        val darkEvil = LyricAnnotationEntity(
            id = 11746017L,
            trackId = 2L,
            lyricFragment = "I got dark, I got evil that rot inside my DNA",
            explanationText = "Dark side analysis..."
        )
        val soundEngine = LyricAnnotationEntity(
            id = 11671585L,
            trackId = 2L,
            lyricFragment = "Yeah, that’s him again, the sound that engine in is like a bird",
            explanationText = "Engine sound analysis..."
        )

        val fullMap = LyricFragmentMatcher.matchAnnotationsToLines(lines, listOf(fightomo, darkEvil, soundEngine))
        assertEquals(3, fullMap.size)
        assertEquals(11666737L, fullMap[0]?.id)
        assertEquals(11746017L, fullMap[12]?.id)
        assertEquals(11671585L, fullMap[15]?.id)
        assertNull(fullMap[19])
    }
}
