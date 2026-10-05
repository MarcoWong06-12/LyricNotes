package com.linernotes.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationServiceTest {

    @Test
    fun testEmptyInputHandling() = runBlocking {
        val service = TranslationService()
        val result = service.translateTrack("", "")
        assertNull(result.translatedTitle)
        assertEquals("", result.translatedLyrics)
    }

    @Test
    fun testBlankTitleHandling() = runBlocking {
        val service = TranslationService()
        val result = service.translateTrack("   ", "")
        assertNull(result.translatedTitle)
        assertEquals("", result.translatedLyrics)
    }

    @Test
    fun testParseIndexedLines_whenAllLinesNumbered() {
        val service = TranslationService()
        val raw = listOf("1. 第一行", "2. 第二行", "3. 第三行")
        val parsed = service.parseIndexedLines(raw, 3)
        assertEquals(listOf("第一行", "第二行", "第三行"), parsed)
    }

    @Test
    fun testParseIndexedLines_whenSensitiveLinesStripNumbersButCountMatches() {
        val service = TranslationService()
        // 模拟有道将中间敏感/脏字句子的序号吞掉，但返回总行数依然等于期望行数的情况
        val raw = listOf(
            "1. 当我们骑在西城，来装备游戏",
            "你自称是个玩咖，但我睡了你老婆",
            "我们干掉坏男孩，这些黑鬼完蛋了",
            "4. 吹牛老爹想见我，脆弱的心被我撕碎"
        )
        val parsed = service.parseIndexedLines(raw, 4)
        assertEquals(
            listOf(
                "当我们骑在西城，来装备游戏",
                "你自称是个玩咖，但我睡了你老婆",
                "我们干掉坏男孩，这些黑鬼完蛋了",
                "吹牛老爹想见我，脆弱的心被我撕碎"
            ),
            parsed
        )
    }

    @Test
    fun testParseIndexedLines_whenBracketFormatUsed() {
        val service = TranslationService()
        val raw = listOf("[#1] 第一行", "[#2] 第二行")
        val parsed = service.parseIndexedLines(raw, 2)
        assertEquals(listOf("第一行", "第二行"), parsed)
    }

    @Test
    fun testParseIndexedLines_whenCountDiffersFillsUnindexed() {
        val service = TranslationService()
        val raw = listOf("1. 第一行", "第二行被吞了序号", "4. 第四行")
        val parsed = service.parseIndexedLines(raw, 4)
        assertEquals(4, parsed.size)
        assertEquals("第一行", parsed[0])
        assertEquals("第二行被吞了序号", parsed[1])
        assertEquals("", parsed[2])
        assertEquals("第四行", parsed[3])
    }

    @Test
    fun testRefineMusicSlang_hipHopIdioms() {
        val service = TranslationService()

        // 1. running for your jewels
        val r1 = service.refineMusicSlang(
            "当我们为你的珠宝而奔跑时，我们不断地来",
            "We keep on coming while we running for your jewels"
        )
        assertEquals("当我们抢夺洗劫你的金链首饰时，我们不断地来", r1)

        // 2. how I'll leave ya
        val r2 = service.refineMusicSlang(
            "小凯撒，去问问你的朋友我怎么离开你",
            "Lil Caesar, go ask your homie how I'll leave ya"
        )
        assertEquals("小凯撒，去问问你的朋友我会把你收拾成什么凄惨死样", r2)

        // 3. steady gunning, busting at fools
        val r3 = service.refineMusicSlang(
            "稳扎稳打，继续打击那些笨蛋，你知道规矩的",
            "Steady gunning, keep on busting at them fools, you know the rules"
        )
        assertEquals("枪火连番扫射，继续狠狠收拾开火痛击那帮蠢货，你知道规矩的", r3)

        // 4. mark-ass
        val r4 = service.refineMusicSlang(
            "大佬斯莫尔斯和少年犯M.A.F.I.A.都是些混蛋",
            "Biggie Smalls and Junior M.A.F.I.A. is some mark-ass bitches"
        )
        assertEquals("大佬斯莫尔斯和少年犯M.A.F.I.A.都是些软蛋窝囊废", r4)
    }
}
