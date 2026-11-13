package com.linernotes.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun testIsInvalidTranslation_filtersQuotaAndLanguageErrors() {
        val service = TranslationService()
        assertTrue(service.isInvalidTranslation(""))
        assertTrue(service.isInvalidTranslation("   "))
        assertTrue(service.isInvalidTranslation("MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS"))
        assertTrue(service.isInvalidTranslation("INVALID SOURCE LANGUAGE PAIR"))
        assertTrue(service.isInvalidTranslation("429 Too Many Requests"))
        assertFalse(service.isInvalidTranslation("发现一张密信，写着阅后即焚"))
    }

    @Test
    fun testTransMartLanguageMappingAndScriptBuckets() {
        val service = TranslationService()
        assertEquals("zh", service.mapTargetToTransMartLang("zh-CN"))
        assertEquals("zh-TW", service.mapTargetToTransMartLang("zh-TW"))
        assertEquals("en", service.mapTargetToTransMartLang("en"))
        assertEquals("ja", service.mapTargetToTransMartLang("ja"))

        assertEquals("en", service.detectLineScriptBucket("Burn after reading this"))
        assertEquals("ja", service.detectLineScriptBucket("夢ならばどれほどよかったでしょう"))
        assertEquals("ko", service.detectLineScriptBucket("오늘 밤도 너를 생각해"))
        assertEquals("auto", service.detectLineScriptBucket("Canción de amor"))
    }

    @Test
    fun testBuildProseBatchChunks_splitsByMaxItemsAndMaxChars() {
        val service = TranslationService()
        assertTrue(service.buildProseBatchChunks(emptyList()).isEmpty())

        val fifteenShort = List(15) { "Paragraph $it" }
        val itemSplit = service.buildProseBatchChunks(fifteenShort, maxItems = 12, maxTotalChars = 3200)
        assertEquals(2, itemSplit.size)
        assertEquals((0 until 12).toList(), itemSplit[0])
        assertEquals((12 until 15).toList(), itemSplit[1])

        val longParagraphs = listOf("a".repeat(1500), "b".repeat(1500), "c".repeat(1500), "d".repeat(3600))
        val charSplit = service.buildProseBatchChunks(longParagraphs, maxItems = 12, maxTotalChars = 3200)
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), charSplit)
    }

    @Test
    fun testIsTranslationTruncated_allowsConciseChineseProseAndRejectsStubs() {
        val service = TranslationService()
        assertTrue(service.isTranslationTruncated("Hello world", "   "))

        // 英文 390 字译成紧凑中文 ~117 字（0.30 字符比）属正常精炼翻译，不应误判截断
        val orig390 = "SZA expresses her deep devotion and attachment to someone who has been supportive and loving towards her. ".repeat(3) +
            "The repetition reinforces her lasting affection across the chorus."
        val zh117 = "她在歌词中倾诉了对这位一直支持并深爱着她的人的炽热依恋与深情托付，即使在面对人生低谷或分离抉择时也依然如此，反复吟唱着敞开心扉的句子强化了这份深刻的情感纽带与安全感，令全曲的情绪层层递进愈发浓烈动人。"
        assertFalse(service.isTranslationTruncated(orig390, zh117))

        // 超长英文若仅产出极短 10 字残句，必须判定为异常截断以触发容灾重试
        assertTrue(service.isTranslationTruncated(orig390, "她深爱着那个人。"))
    }
}
