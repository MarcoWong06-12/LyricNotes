package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilingualSentenceAlignerTest {

    @Test
    fun testSingleSentenceAlignment() {
        val original = "Kendrick Lamar introduces the album's themes here."
        val translation = "肯德里克·拉马尔在这里介绍了专辑的主题。"

        val result = BilingualSentenceAligner.align(original, translation)
        assertEquals(1, result.size)
        assertEquals(1, result[0].units.size)
        assertEquals("肯德里克·拉马尔在这里介绍了专辑的主题。", result[0].units[0].chinese)
        assertEquals("Kendrick Lamar introduces the album's themes here.", result[0].units[0].original)
    }

    @Test
    fun testMultiSentenceOneToOneAlignment() {
        val original = "Kendrick Lamar addresses his struggles and fame. And he reflects on the people who supported him along the way. He questions loyalty and integrity."
        val translation = "肯德里克·拉马尔讲述了他的挣扎和成名。他回顾了一路支持他的人。他质疑忠诚和正直。"

        val result = BilingualSentenceAligner.align(original, translation)
        assertEquals(1, result.size)
        assertEquals(3, result[0].units.size)

        assertEquals("肯德里克·拉马尔讲述了他的挣扎和成名。", result[0].units[0].chinese)
        assertEquals("Kendrick Lamar addresses his struggles and fame.", result[0].units[0].original)

        assertEquals("他回顾了一路支持他的人。", result[0].units[1].chinese)
        assertEquals("And he reflects on the people who supported him along the way.", result[0].units[1].original)

        assertEquals("他质疑忠诚和正直。", result[0].units[2].chinese)
        assertEquals("He questions loyalty and integrity.", result[0].units[2].original)
    }

    @Test
    fun testAbbreviationProtectionInSentenceSplitting() {
        val original = "The song was produced by Dr. Dre and Sounwave. It features additional vocals from Rihanna."
        val translation = "这首歌由 Dr. Dre 和 Sounwave 制作。蕾哈娜参与了客串演唱。"

        val result = BilingualSentenceAligner.align(original, translation)
        assertEquals(1, result.size)
        assertEquals(2, result[0].units.size)

        assertEquals("这首歌由 Dr. Dre 和 Sounwave 制作。", result[0].units[0].chinese)
        assertEquals("The song was produced by Dr. Dre and Sounwave.", result[0].units[0].original)

        assertEquals("蕾哈娜参与了客串演唱。", result[0].units[1].chinese)
        assertEquals("It features additional vocals from Rihanna.", result[0].units[1].original)
    }

    @Test
    fun testMismatchedSentenceCountsGracefulFallback() {
        // When English has 2 sentences but Chinese translation is fused into 1 sentence
        val original = "First sentence here. Second sentence here."
        val translation = "这是两句话合并后的中文翻译结果。"

        val result = BilingualSentenceAligner.align(original, translation)
        assertEquals(1, result.size)
        assertEquals(1, result[0].units.size)
        assertEquals("这是两句话合并后的中文翻译结果。", result[0].units[0].chinese)
        assertEquals("First sentence here. Second sentence here.", result[0].units[0].original)
    }

    @Test
    fun testMultiParagraphAlignment() {
        val original = """
            On BLOOD., Kendrick Lamar examines his own mortality and the thin line between good and evil. He tells a story about helping a blind woman.

            When Kendrick approaches her to help, she reveals that what she has lost is her life. This allegory sets the stage for the rest of the album.
        """.trimIndent()

        val translation = """
            在《BLOOD.》中，肯德里克·拉马尔探讨了生死以及善恶之间的界限。他讲述了一个试图帮助盲人女子的故事。

            当肯德里克走上前去帮助她时，她透露自己失去的正是生命。这个寓言为整张专辑拉开了序幕。
        """.trimIndent()

        val result = BilingualSentenceAligner.align(original, translation)
        assertEquals(2, result.size)
        assertEquals(2, result[0].units.size)
        assertEquals(2, result[1].units.size)

        assertEquals("在《BLOOD.》中，肯德里克·拉马尔探讨了生死以及善恶之间的界限。", result[0].units[0].chinese)
        assertEquals("On BLOOD., Kendrick Lamar examines his own mortality and the thin line between good and evil.", result[0].units[0].original)
    }

    @Test
    fun testNullOrEmptyTranslation() {
        val original = "Only original English text exists here."
        val result = BilingualSentenceAligner.align(original, null)
        assertEquals(1, result.size)
        assertEquals(1, result[0].units.size)
        assertEquals("", result[0].units[0].chinese)
        assertEquals("Only original English text exists here.", result[0].units[0].original)
    }
}
