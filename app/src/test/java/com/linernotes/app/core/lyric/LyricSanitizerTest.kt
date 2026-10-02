package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSanitizerTest {

    @Test
    fun `test dictionary decensoring for common masked words`() {
        assertEquals("Life's a Bitch", LyricSanitizer.decensorLine("Life's a B***h"))
        assertEquals("life's a bitch", LyricSanitizer.decensorLine("life's a b***h"))
        assertEquals("straight out the fucking dungeons of rap", LyricSanitizer.decensorLine("straight out the ****ing dungeons of rap"))
        assertEquals("Where fake niggas don't make it back", LyricSanitizer.decensorLine("Where fake n****s don't make it back"))
        assertEquals("I don't know how to start this shit, yo, now", LyricSanitizer.decensorLine("I don't know how to start this s***, yo, now"))
    }

    @Test
    fun `test reference based decensoring for pure asterisks`() {
        val censored = "when you're gonna go Life's a ***** and then you die;"
        val reference = "when you're gonna go Life's a bitch and then you die;"
        val result = LyricSanitizer.decensorLine(censored, reference)
        assertEquals("when you're gonna go Life's a bitch and then you die;", result)
    }

    @Test
    fun `test full lyrics decensoring with timestamps and chinese translation preservation`() {
        val censoredLrc = """
            [01:04.06] Life's a ***** and then you die, that's why we get high
            [01:09.24] Life's a B***h and then you die, that's why we puff lye
            [01:14.58] Where fake n****s don't make it back
        """.trimIndent()

        val refLrc = """
            [01:04.06] Life's a bitch and then you die, that's why we get high
            [01:09.24] Life's a bitch and then you die, that's why we puff lye
            [01:14.58] Where fake niggas don't make it back
        """.trimIndent()

        val sanitized = LyricSanitizer.decensorLyrics(censoredLrc, refLrc)
        assertTrue(sanitized.contains("Life's a bitch and then you die, that's why we get high"))
        assertTrue(sanitized.contains("Life's a Bitch and then you die, that's why we puff lye"))
        assertTrue(sanitized.contains("Where fake niggas don't make it back"))
    }

    @Test
    fun `test decensor title`() {
        assertEquals("Life's a Bitch", LyricSanitizer.decensorTitle("Life's a B***h"))
    }

    @Test
    fun `test king kunta iconic pure asterisks decensoring without reference`() {
        val line1 = "I don't want you monkey mouth ************* sittin' in my throne again"
        val clean1 = LyricSanitizer.decensorLine(line1)
        assertFalse(clean1.contains("*"))
        assertTrue(clean1.contains("motherfuckers"))

        val line2 = "Aye aye ***** whats happenin' ***** K Dot back in the hood *****"
        val clean2 = LyricSanitizer.decensorLine(line2)
        assertFalse(clean2.contains("*"))
        assertTrue(clean2.contains("nigga"))
    }

    @Test
    fun `test king kunta chinese translation decensoring`() {
        val zhLine1 = "我不想要妳猴嘴*************再坐在我的寶座上"
        val cleanZh1 = LyricSanitizer.decensorChineseLine(zhLine1, "I don't want you monkey mouth motherfuckers sittin' in my throne again")
        assertFalse(cleanZh1.contains("*"))
        assertTrue(cleanZh1.contains("混蛋"))

        val zhLine2 = "是啊是啊*****發生了什麼事***** K點回到引擎蓋*****"
        val cleanZh2 = LyricSanitizer.decensorChineseLine(zhLine2, "Aye aye nigga whats happenin' nigga K Dot back in the hood nigga")
        assertFalse(cleanZh2.contains("*"))
        assertTrue(cleanZh2.contains("兄弟"))
    }

    @Test
    fun `test decensor lyrics with genius referent fragments`() {
        val censoredLrc = """
            [00:15.00] I don't want you monkey mouth ************* sittin' in my throne again
            [00:20.00] Aye aye ***** whats happenin' ***** K Dot back in the hood *****
        """.trimIndent()

        val geniusFragments = listOf(
            "I don't want you monkey-mouth motherfuckers sittin' in my throne again",
            "Aye, aye, nigga, what's happenin'? Nigga, K-Dot back in the hood, nigga"
        )

        val cleaned = LyricSanitizer.decensorLyrics(censoredLrc, geniusFragments)
        assertFalse(cleaned.contains("*"))
        assertTrue(cleaned.contains("motherfuckers"))
        assertTrue(cleaned.contains("nigga"))
    }

    @Test
    fun `test zero asterisks guarantee on unknown masked words`() {
        val testLine = "You ********** better watch out, he is ******* crazy"
        val cleaned = LyricSanitizer.decensorLine(testLine)
        assertFalse(cleaned.contains("*"))

        val testZh = "你这个************快给我***闭***嘴"
        val cleanZh = LyricSanitizer.decensorChineseLine(testZh)
        assertFalse(cleanZh.contains("*"))
    }
}
