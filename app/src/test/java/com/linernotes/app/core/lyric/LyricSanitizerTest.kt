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
    fun `test family matters that shit decensoring`() {
        val line = "And begged 'em to sue me and get that **** down"
        val clean = LyricSanitizer.decensorLine(line)
        assertEquals("And begged 'em to sue me and get that shit down", clean)
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

    @Test
    fun `test sabrina carpenter please please please decensoring with partially masked letters`() {
        val line = "I beg you, don't embarrass me, mother****er, oh"
        val clean = LyricSanitizer.decensorLine(line)
        assertEquals("I beg you, don't embarrass me, motherfucker, oh", clean)
    }

    @Test
    fun `test radio clean reference does not corrupt partially masked profanity`() {
        val censored = "I beg you, don't embarrass me, mother****er, oh"
        val radioRef = "I beg you don't embarrass me little sucker oh"
        val result = LyricSanitizer.decensorLine(censored, radioRef)
        assertEquals("I beg you, don't embarrass me, motherfucker, oh", result)
    }

    @Test
    fun `test radio clean euphemism reversal with pure asterisks`() {
        val censored = "I beg you, don't embarrass me, *************, oh"
        val radioRef = "I beg you don't embarrass me little sucker oh"
        val result = LyricSanitizer.decensorLine(censored, radioRef)
        assertEquals("I beg you, don't embarrass me, motherfucker, oh", result)
    }

    @Test
    fun `test radio clean line reversal without asterisks`() {
        val radio1 = "I beg you, don't embarrass me, little sucker, oh"
        assertEquals("I beg you, don't embarrass me, motherfucker, oh", LyricSanitizer.decensorLine(radio1))

        val radio2 = "I beg you, don't embarrass me, little, oh"
        assertEquals("I beg you, don't embarrass me, motherfucker, oh", LyricSanitizer.decensorLine(radio2))

        val radio3 = "Why the freak did you do that?"
        assertEquals("Why the fuck did you do that?", LyricSanitizer.decensorLine(radio3))
    }

    @Test
    fun `test translation hint decensoring when reference is radio edited`() {
        val censored = "[01:16.166] I beg you, don't embarrass me, *************, oh"
        val radioRef = "[01:16.28] I beg you don't embarrass me little sucker oh"
        val trans = "[01:16.166] 求妳了 別讓我難堪 混蛋 噢"
        val result = LyricSanitizer.decensorLine(censored, radioRef, trans)
        assertEquals("[01:16.166] I beg you, don't embarrass me, motherfucker, oh", result)
    }

    @Test
    fun `test translation hint with multiple profanities and traditional chinese`() {
        val censored = "Cut that **** out and stop this ****"
        val trans = "少搞那套狗屎把戲，別再他媽的胡鬧了"
        val result = LyricSanitizer.decensorLine(censored, "", trans)
        assertEquals("Cut that shit out and stop this fuck", result)
    }

    @Test
    fun `test blank gap and dropped word restoration without asterisks`() {
        val censoredWithDoubleSpace = "I don't give a   about you"
        val ref = "I don't give a fuck about you"
        val result1 = LyricSanitizer.decensorLine(censoredWithDoubleSpace, ref)
        assertEquals("I don't give a fuck about you", result1)

        val censoredMissingWord = "I don't give a about you"
        val result2 = LyricSanitizer.decensorLine(censoredMissingWord, ref)
        assertEquals("I don't give a fuck about you", result2)

        val trailingDropped = "What the"
        val trailingRef = "What the fuck"
        val result3 = LyricSanitizer.decensorLine(trailingDropped, trailingRef)
        assertEquals("What the fuck", result3)
    }

    @Test
    fun `test yrc inline word tags splitting censored profanity in hit em up`() {
        val rawYrc = """
            [25440,2880](25440,270,0)Biggie (25710,270,0)Smalls (25980,90,0)and (26070,270,0)Junior (26850,60,0)is (26910,180,0)some (27090,330,0)mark (27420,120,0)a(27540,70,0)*(27610,80,0)* (27690,210,0)b(27900,0,0)*(27900,0,0)*(27900,330,0)ches(28230,90,0)
            [38520,2010](38520,240,0)Lil' (38760,330,0)Kim (39090,240,0)don't (39330,120,0)f(39450,0,0)*(39450,0,0)*(39450,90,0)k (39540,270,0)around (39810,120,0)with (39930,300,0)real (40230,300,0)G's
            [40530,3060](40530,180,0)Quick (40710,90,0)to (40800,300,0)snatch (41100,120,0)yo' (41220,360,0)ugly (41580,120,0)a(41700,60,0)*(41760,60,0)* (41820,180,0)off (42000,120,0)the (42120,360,0)streets (42480,60,0) (42540,210,0)so (42750,60,0)f(42810,0,0)*(42810,0,0)*(42810,210,0)k (43020,30,0) (43050,360,0)peace(43410,180,0)
            [96030,2400](96030,150,0)And (96180,90,0)I'm (96270,360,0)smokin' (97440,60,0)in (97500,330,0)front (97830,60,0)of (97890,120,0)you (98010,240,0)ni(98250,0,0)*(98250,0,0)*(98250,30,0)a(98280,150,0)
        """.trimIndent()

        assertTrue(LyricSanitizer.hasCensorship(rawYrc))
        val cleaned = LyricSanitizer.decensorLyrics(rawYrc)
        assertFalse(cleaned.contains("*"))

        val lines = cleaned.lines()
        assertEquals("Biggie Smalls and Junior is some mark ass bitches", LyricWordParser.cleanInlineTimestamps(lines[0]))
        assertEquals("Lil' Kim don't fuck around with real G's", LyricWordParser.cleanInlineTimestamps(lines[1]))
        assertEquals("Quick to snatch yo' ugly ass off the streets  so fuck  peace", LyricWordParser.cleanInlineTimestamps(lines[2]))
        assertEquals("And I'm smokin' in front of you nigga", LyricWordParser.cleanInlineTimestamps(lines[3]))

        // Verify restored profanity merges sub-word tags into one intact word unit so UI never wraps mid-word
        val parsedLine2 = LyricWordParser.parseLineWords(lines[1], 38520L, 40530L)
        val fuckWord = parsedLine2.firstOrNull { it.text.trim() == "fuck" }
        assertTrue(fuckWord != null)
        assertEquals(39330L, fuckWord!!.startTimeMs)
        assertEquals(210L, fuckWord.durationMs)
    }

    @Test
    fun `test pure asterisk split yrc krc elrc and fullwidth asterisks`() {
        val pureYrc = "[35700,2010](35700,240,0)Lil' (35940,330,0)Kim, (36270,330,0)don't (36600,30,0)*(36630,30,0)*(36660,30,0)*(36690,30,0)* (36720,240,0)around"
        assertTrue(LyricSanitizer.hasCensorship(pureYrc))
        val cleanedPure = LyricSanitizer.decensorLine(pureYrc)
        assertEquals("[35700,2010](35700,240,0)Lil' (35940,330,0)Kim, (36270,330,0)don't (36600,120,0)fuck (36720,240,0)around", cleanedPure)

        val krcLine = "[1000,1500]<0,200,0>What <200,200,0>the <400,100,0>f<500,50,0>*<550,50,0>*<600,100,0>k"
        assertTrue(LyricSanitizer.hasCensorship(krcLine))
        assertEquals("[1000,1500]<0,200,0>What <200,200,0>the <400,300,0>fuck", LyricSanitizer.decensorLine(krcLine))

        val elrcLine = "[00:10.00]<00:10.00>f<00:10.10>**<00:10.20>kin'"
        assertTrue(LyricSanitizer.hasCensorship(elrcLine))
        assertEquals("[00:10.00]<00:10.00>fuckin'", LyricSanitizer.decensorLine(elrcLine))

        val fullwidth = "Lil' Kim don't f＊＊k around"
        assertTrue(LyricSanitizer.hasCensorship(fullwidth))
        assertEquals("Lil' Kim don't fuck around", LyricSanitizer.decensorLine(fullwidth))

        val tagAfter = "don't(100,100) f(200,50)*(250,50)*(300,50)k(350,50)"
        assertTrue(LyricSanitizer.hasCensorship(tagAfter))
        assertEquals("don't(100,100) fuck(200,200)", LyricSanitizer.decensorLine(tagAfter))
    }
}
