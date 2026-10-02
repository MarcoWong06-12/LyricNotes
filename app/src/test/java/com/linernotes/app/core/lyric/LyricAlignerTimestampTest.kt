package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricAlignerTimestampTest {

    @Test
    fun `test timestamp based alignment prevents translation shift on intro credits`() {
        // 模拟网易云音乐等平台原版歌词（带有制作人信息与引言）
        val origLrc = """
            [00:00.00] 制作人 : DJ Premier/Nas
            [00:00.54] 作词 : Christopher Martin/Nasir Jones
            [00:01.09] 作曲 : Christopher Martin/Nasir Jones
            [00:01.64] Yeah yeah, Ayo black it's time (word?)
            [00:06.45] (Word, it's time nigga?)
            [01:03.00] Once they caught us off guard, the Mac-10 was in the grass and
            [01:05.12] I ran like a cheetah, with thoughts of an assassin
            [01:27.77] And it was full of children, prob'ly couldn't see as high as I be
            [01:30.45] (So, what you sayin'?) It's like the game ain't the same
        """.trimIndent()

        // 译文（仅对正文歌词提供翻译，前奏制作人信息无翻译）
        val transLrc = """
            [00:01.64] 是啊是啊，Ayo黑鬼，是时候了（真的吗？）
            [00:06.45] （真的，是时候了吗？）
            [01:03.00] 一旦我们猝不及防，Mac-10就落在草丛里
            [01:05.12] 我像猎豹一样奔跑，脑海中浮现杀手的念头
            [01:27.77] 里面坐满了孩子，他们可能看不见我这么高
            [01:30.45] （你在说什么？）就像这场游戏已经不再相同
        """.trimIndent()

        val aligned = LyricAligner.align(origLrc, transLrc)

        // 验证前奏制作人行没有被盗取译文
        val producerLine = aligned.find { it.original.contains("制作人") }
        assertTrue(producerLine != null)
        assertEquals("", producerLine?.translation)

        // 验证第 1:03 行匹配的是正确的译文，而不是后面的"里面坐满了孩子"
        val mac10Line = aligned.find { it.original.contains("Once they caught us off guard") }
        assertTrue(mac10Line != null)
        assertEquals("一旦我们猝不及防，Mac-10就落在草丛里", mac10Line?.translation)

        // 验证"里面坐满了孩子"精准对应 1:27 行
        val childrenLine = aligned.find { it.original.contains("And it was full of children") }
        assertTrue(childrenLine != null)
        assertEquals("里面坐满了孩子，他们可能看不见我这么高", childrenLine?.translation)
    }

    @Test
    fun `test proximity timestamp alignment within 500ms`() {
        val origLrc = """
            [01:04.06] Life's a bitch and then you die
            [01:09.24] that's why we get high
        """.trimIndent()

        // 译文时间戳有轻微几十毫秒偏差
        val transLrc = """
            [01:04.10] 人生就是个混账，最后难逃一死
            [01:09.20] 这就是我们吸大麻的原因
        """.trimIndent()

        val aligned = LyricAligner.align(origLrc, transLrc)
        assertEquals("人生就是个混账，最后难逃一死", aligned[0].translation)
        assertEquals("这就是我们吸大麻的原因", aligned[1].translation)
    }
}
