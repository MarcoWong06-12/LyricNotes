package com.linernotes.app.data.repository

import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.playback.PlaybackStateManager
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.domain.model.BilingualLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueAndTranslationGuardTest {

    @Test
    fun updateQueueItems_preservesTrackMetadataAndEpochAtomically() {
        val manager = PlaybackStateManager()
        val playing = TrackPlaybackState(
            title = "Stop The Wedding!",
            artist = "Ashe",
            durationMs = 198840L,
            isPlaying = false,
            lastUpdateTimeMs = 0L,
            coverUrl = "https://example.com/ashe.jpg"
        )
        manager.updateState(playing)
        val initialEpoch = manager.playbackState.value.trackEpoch
        assertTrue(initialEpoch > 0L)

        // 用户在播放途中把另一首歌加入队列，触发原子队列更新
        val queued = listOf(
            QueueTrackItem(id = 1L, title = "Espresso", artist = "Sabrina Carpenter")
        )
        manager.updateQueueItems(queued)
        manager.updatePlaybackModes(isShuffleActive = true, repeatMode = 1)

        val current = manager.playbackState.value
        assertEquals("Stop The Wedding!", current.title)
        assertEquals("Ashe", current.artist)
        assertEquals("https://example.com/ashe.jpg", current.coverUrl)
        assertEquals(initialEpoch, current.trackEpoch)
        assertEquals(queued, current.queueItems)
        assertTrue(current.isShuffleActive)
        assertEquals(1, current.repeatMode)
    }

    @Test
    fun rawTranslationLacking_rejectsTimestampOnlyAndPartialOutputs() {
        val orig = """
            [00:07.69] I found a secret note, "Burn after reading this"
            [00:11.07] It's supernatural, the feeling's sinking in
            [00:14.64] This isn't cold feet, these aren't fireworks
            [00:18.03] The sky is menacing, and that's an iceberg
        """.trimIndent()

        // 全部引擎失败后若仅返回时间戳骨架，必须被判为缺翻译
        val timestampOnly = """
            [00:07.69]
            [00:11.07]
            [00:14.64]
            [00:18.03]
        """.trimIndent()
        assertTrue(NowPlayingRepository.isRawTranslationLacking(orig, timestampOnly))

        // 仅翻译了 1/4 (<50%) 也必须触发重译补全
        val partialTrans = """
            [00:07.69] 发现一张密信 写着阅后即焚
            [00:11.07]
            [00:14.64]
            [00:18.03]
        """.trimIndent()
        assertTrue(NowPlayingRepository.isRawTranslationLacking(orig, partialTrans))

        val completeTrans = """
            [00:07.69] 发现一张密信 写着阅后即焚
            [00:11.07] 诡异感觉逐渐侵入心门
            [00:14.64] 这并非临阵退缩 也不是烟花热忱
            [00:18.03] 天空阴沉暗涌 犹如冰山横陈
        """.trimIndent()
        assertFalse(NowPlayingRepository.isRawTranslationLacking(orig, completeTrans))
    }

    @Test
    fun translationIncomplete_detectsEmptyShortSongsAndSkipsNativeChinese() {
        // 即使只有 3 行歌词且全部缺译，也必须标记为残缺缓存
        val shortUntranslated = listOf(
            BilingualLyricLine(1, "I found a secret note", "", startTimeMs = 7690L),
            BilingualLyricLine(2, "Burn after reading this", "", startTimeMs = 11070L),
            BilingualLyricLine(3, "It's supernatural", "", startTimeMs = 14640L)
        )
        assertTrue(NowPlayingRepository.isTranslationIncomplete(shortUntranslated, "zh-CN"))

        // 仅有 1/4 行有译文（25%，以前 <=0.25 边界易漏过）也必须判为残缺
        val quarterTranslated = listOf(
            BilingualLyricLine(1, "Line one here", "第一行", startTimeMs = 1000L),
            BilingualLyricLine(2, "Line two here", "", startTimeMs = 2000L),
            BilingualLyricLine(3, "Line three here", "", startTimeMs = 3000L),
            BilingualLyricLine(4, "Line four here", "", startTimeMs = 4000L)
        )
        assertTrue(NowPlayingRepository.isTranslationIncomplete(quarterTranslated, "zh-CN"))

        // 原生中文歌曲以中文为目标语种时不应误判为缺译
        val nativeChinese = listOf(
            BilingualLyricLine(1, "故事的小黄花", "", startTimeMs = 1000L),
            BilingualLyricLine(2, "从出生那年就飘着", "", startTimeMs = 2000L),
            BilingualLyricLine(3, "童年的荡秋千", "", startTimeMs = 3000L),
            BilingualLyricLine(4, "随记忆一直晃到现在", "", startTimeMs = 4000L)
        )
        assertFalse(NowPlayingRepository.isTranslationIncomplete(nativeChinese, "zh-CN"))
    }

    @Test
    fun alignLrcTimestamps_handlesThreeDigitMillisAndTranslatorTag() {
        val orig = """
            [00:07.697] I found a secret note, "Burn after reading this"
            [00:11.072] It's supernatural, the feeling's sinking in
        """.trimIndent()
        val tlyric = """
            [by:stillworkingonit]
            [00:07.697] 发现一张密信 写着阅后即焚
            [00:11.072] 诡异感觉逐渐侵入心门
        """.trimIndent()

        val aligned = LyricAligner.alignLrcTimestamps(orig, tlyric)
        assertTrue(aligned.first.contains("Burn after reading this"))
        assertTrue(aligned.second.contains("发现一张密信"))
        assertTrue(aligned.second.contains("诡异感觉逐渐侵入心门"))
    }
}
