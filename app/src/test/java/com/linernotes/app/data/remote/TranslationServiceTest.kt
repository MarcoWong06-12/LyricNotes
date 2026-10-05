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

        // 1. no cap
        val r1 = service.refineMusicSlang(
            "我说的是真的没有帽子",
            "I'm telling the truth no cap"
        )
        assertEquals("我说的是真的绝无虚言(不吹牛)", r1)

        // 2. iced out
        val r2 = service.refineMusicSlang(
            "我的手腕结冰了",
            "My wrist is iced out"
        )
        assertEquals("我的手腕满身闪耀钻石珠宝", r2)

        // 3. drop top
        val r3 = service.refineMusicSlang(
            "开着一辆下沉顶部",
            "Riding in a drop top"
        )
        assertEquals("开着一辆敞篷跑车", r3)

        // 4. pull up
        val r4 = service.refineMusicSlang(
            "兄弟们开着豪车拉起",
            "The homies pull up in foreigns"
        )
        assertEquals("兄弟们开着豪车驱车杀到", r4)

        // 5. catch a body
        val r5 = service.refineMusicSlang(
            "不想抓住一具尸体",
            "Don't wanna catch a body"
        )
        assertEquals("不想背上人命重案", r5)
    }
}
