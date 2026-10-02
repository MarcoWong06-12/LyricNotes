package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricAlignerTest {

    @Test
    fun testTimestampExtraction() {
        val ms1 = LyricAligner.extractTimestampMs("[01:23.45] Hello World")
        assertEquals(83450L, ms1)

        val ms2 = LyricAligner.extractTimestampMs("[00:05.120] Start")
        assertEquals(5120L, ms2)

        val ms3 = LyricAligner.extractTimestampMs("Plain text without time")
        assertEquals(null, ms3)
    }

    @Test
    fun testTimestampFormatting() {
        assertEquals("[01:23.45]", LyricAligner.formatTimestamp(83450L))
        assertEquals("[00:05.12]", LyricAligner.formatTimestamp(5120L))
        assertEquals("[00:00.00]", LyricAligner.formatTimestamp(0L))
    }

    @Test
    fun testAlignWithTimestamps() {
        val orig = """
            [00:10.00] Line one original
            [00:25.50] Line two original
            [00:40.00] Line three original
        """.trimIndent()

        val trans = """
            [00:10.00] 第一句译文
            [00:25.50] 第二句译文
            [00:40.00] 第三句译文
        """.trimIndent()

        val aligned = LyricAligner.align(orig, trans)
        assertEquals(3, aligned.size)

        assertEquals("Line one original", aligned[0].original)
        assertEquals("第一句译文", aligned[0].translation)
        assertEquals(10000L, aligned[0].startTimeMs)

        assertEquals("Line two original", aligned[1].original)
        assertEquals("第二句译文", aligned[1].translation)
        assertEquals(25500L, aligned[1].startTimeMs)

        assertEquals("Line three original", aligned[2].original)
        assertEquals("第三句译文", aligned[2].translation)
        assertEquals(40000L, aligned[2].startTimeMs)
    }

    @Test
    fun testAlignLrcTimestampsPreservation() {
        val origLrc = """
            [00:01.00] 作词: 椎名林檎
            [00:02.00] 作曲: 椎名林檎
            [00:15.00] 虚言症の少女
            [00:25.00] 白昼夢を見る
        """.trimIndent()

        val transLrc = """
            [00:15.00] 患妄想症的少女
            [00:25.00] 做着白日梦
        """.trimIndent()

        val (alignedOrig, alignedTrans) = LyricAligner.alignLrcTimestamps(origLrc, transLrc)
        assertTrue(alignedOrig.contains("[00:15.00]"))
        assertTrue(alignedOrig.contains("虚言症の少女"))
        assertTrue(alignedTrans.contains("[00:15.00]"))
        assertTrue(alignedTrans.contains("患妄想症的少女"))
    }
}
