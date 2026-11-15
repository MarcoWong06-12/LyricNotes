package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testRefusalText_detectsMyMemoryQuotaWarning() {
        val warning = "MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY. NEXT AVAILABLE IN 01 HOURS 23 MINUTES 15 SECONDS VISIT HTTPS://MYMEMORY.TRANSLATED.NET/DOC/USAGELIMITS.PHP TO TRANSLATE MORE"
        assertTrue(LyricAligner.isRefusalText(warning))
        assertTrue(LyricAligner.isInvalidTranslationLine(warning))
    }

    @Test
    fun testAlign_filtersMyMemoryWarningFromTranslation() {
        val orig = """
            [00:10.00] 貴方に降り註ぐものが譬え
            [00:25.00] 雨だろうが運命だろうが
        """.trimIndent()

        val warningTrans = """
            [00:10.00] MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY. NEXT AVAILABLE IN 01 HOURS 23 MINUTES 15 SECONDS VISIT HTTPS://MYMEMORY.TRANSLATED.NET/DOC/USAGELIMITS.PHP TO TRANSLATE MORE
            [00:25.00] MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY. NEXT AVAILABLE IN 01 HOURS 23 MINUTES 15 SECONDS VISIT HTTPS://MYMEMORY.TRANSLATED.NET/DOC/USAGELIMITS.PHP TO TRANSLATE MORE
        """.trimIndent()

        val aligned = LyricAligner.align(orig, warningTrans)
        assertEquals(2, aligned.size)
        assertEquals("貴方に降り註ぐものが譬え", aligned[0].original)
        assertEquals("", aligned[0].translation)
        assertEquals("雨だろうが運命だろうが", aligned[1].original)
        assertEquals("", aligned[1].translation)
    }

    @Test
    fun testParseTimedLines_andAlign_supportsYrcKrcQrcMsHeadersAndClampsWords() {
        val yrc = """
            {"t":0,"c":[{"tx":"作词: 某人"}]}
            [1000,1500](1000,500,0)作词: 某人
            [10000,2000](10000,600,0)夜(10600,800,0)空(11400,900,0)下
            [12500,2000]<0,500,0>星<500,700,0>光
        """.trimIndent()
        val trans = """
            [00:10.00] 夜空之下
            [00:12.50] 星光闪烁
        """.trimIndent()

        val parsed = LyricAligner.parseTimedLines(yrc)
        assertEquals(3, parsed.size)
        assertEquals(1000L, parsed[0].ms)
        assertEquals(1500L, parsed[0].durationMs)
        assertEquals(10000L, parsed[1].ms)
        assertEquals(2000L, parsed[1].durationMs)

        val (alignedOrig, alignedTrans) = LyricAligner.alignLrcTimestamps(yrc, trans)
        assertFalse(alignedOrig.contains("作词"))
        assertTrue(alignedOrig.contains("[00:10.00]"))
        assertTrue(alignedTrans.contains("夜空之下"))

        val bilingual = LyricAligner.align(alignedOrig, alignedTrans)
        assertEquals(2, bilingual.size)
        assertEquals("夜空下", bilingual[0].original)
        assertEquals("夜空之下", bilingual[0].translation)
        assertEquals(3, bilingual[0].words.size)
        assertTrue(bilingual[0].words.all {
            it.startTimeMs >= bilingual[0].startTimeMs!! &&
                it.startTimeMs + it.durationMs <= bilingual[0].endTimeMs!!
        })
        assertFalse(bilingual[0].original.contains("(10000"))

        assertEquals("星光", bilingual[1].original)
        assertEquals(2, bilingual[1].words.size)
        assertEquals(12500L, bilingual[1].words[0].startTimeMs)
        assertEquals(13000L, bilingual[1].words[1].startTimeMs)
    }
}
