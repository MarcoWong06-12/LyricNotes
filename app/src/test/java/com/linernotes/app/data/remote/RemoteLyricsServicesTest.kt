package com.linernotes.app.data.remote

import com.linernotes.app.core.lyric.LyricAligner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class RemoteLyricsServicesTest {

    @Test
    fun testMetadataService_selectBestItunesCoverRejectsMismatchAndUpgradesResolution() {
        val candidates = listOf(
            MetadataService.ItunesTrackCandidate(
                trackName = "Completely Unrelated Song",
                artistName = "Other Singer",
                artworkUrl100 = "https://is1-ssl.mzstatic.com/image/thumb/wrong/100x100bb.jpg"
            ),
            MetadataService.ItunesTrackCandidate(
                trackName = "Blinding Lights",
                artistName = "The Weeknd",
                artworkUrl100 = "https://is1-ssl.mzstatic.com/image/thumb/right/100x100bb.jpg"
            )
        )

        val picked = MetadataService.selectBestItunesCover(
            candidates = candidates,
            targetTitle = "Blinding Lights (Official Audio)",
            targetArtist = "The Weeknd"
        )
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/right/600x600bb.jpg", picked)

        // 只有完全不匹配的候选时必须拒绝返回 null，不可盲拿 results[0]
        val rejected = MetadataService.selectBestItunesCover(
            candidates = listOf(candidates[0]),
            targetTitle = "Blinding Lights",
            targetArtist = "The Weeknd"
        )
        assertNull(rejected)
    }

    @Test
    fun testMetadataService_selectBestItunesCoverSupportsKoreanHangul() {
        val candidates = listOf(
            MetadataService.ItunesTrackCandidate(
                trackName = "밤편지",
                artistName = "아이유 (IU)",
                artworkUrl100 = "https://is1-ssl.mzstatic.com/image/thumb/iu/100x100bb.jpg"
            )
        )

        val picked = MetadataService.selectBestItunesCover(
            candidates = candidates,
            targetTitle = "밤편지",
            targetArtist = "아이유"
        )
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/iu/600x600bb.jpg", picked)
    }

    @Test
    fun testNetEaseLyricsService_prefersValidYrcAndFallsBackSafelyToLrc() {
        val validYrc = """
            [10000,2000](10000,500,0)Hel(10500,500,0)lo (11000,1000,0)World
            [12500,2000](12500,600,0)Good(13100,900,0)bye
        """.trimIndent()
        val stdLrc = """
            [00:10.00] Hello World
            [00:12.50] Goodbye
        """.trimIndent()
        val transLrc = """
            [00:10.00] 你好世界
            [00:12.50] 再见
        """.trimIndent()

        assertTrue(NetEaseLyricsService.isValidWordTimedYrc(validYrc))
        assertFalse(NetEaseLyricsService.isValidWordTimedYrc("[00:10.00] Plain LRC only"))
        assertFalse(NetEaseLyricsService.isValidWordTimedYrc("[10000,2000](10000,500,0)bad\u0000text"))

        val chosenYrc = NetEaseLyricsService.selectPreferredNetEaseLyricPayload(
            origLrc = stdLrc,
            transLrc = transLrc,
            yrcLrc = validYrc,
            ytLrc = ""
        )
        assertNotNull(chosenYrc)
        assertEquals(validYrc, chosenYrc?.first)
        assertEquals(transLrc, chosenYrc?.second)

        // 当 yrc 稀疏残缺（显著少于 lrc 行数）或无效时，回退至标准 LRC
        val sparseYrc = "[10000,2000](10000,500,0)OnlyOneLine"
        val denseLrc = (1..6).joinToString("\n") { i -> "[00:1$i.00] Lyric line $i" }
        val fallbackToLrc = NetEaseLyricsService.selectPreferredNetEaseLyricPayload(
            origLrc = denseLrc,
            transLrc = transLrc,
            yrcLrc = sparseYrc,
            ytLrc = ""
        )
        assertEquals(denseLrc, fallbackToLrc?.first)
        assertEquals(transLrc, fallbackToLrc?.second)
    }

    @Test
    fun testKugouLyricsService_decodesValidKrcAndRejectsCorruptedPayloads() {
        val sampleKrc = """
            [ti:Test Song]
            [10000,2000]<0,500,0>夜<500,500,0>空<1000,1000,0>下
            [13000,2500]<0,700,0>星<700,800,0>光
        """.trimIndent()

        val encoded = encodeSampleKrc(sampleKrc)
        val decoded = KugouLyricsService.decodeKrc(encoded)
        assertEquals(sampleKrc, decoded)

        // 魔数错误、非法 Base64 或加密/不含逐字标签的正文必须被拒绝
        assertNull(KugouLyricsService.decodeKrc("not-valid-base64!!!"))
        val badMagic = Base64.getEncoder().encodeToString("bad1hello".toByteArray(Charsets.UTF_8))
        assertNull(KugouLyricsService.decodeKrc(badMagic))

        val plainNoWordTags = encodeSampleKrc("[00:10.00] Not word timed krc")
        assertNull(KugouLyricsService.decodeKrc(plainNoWordTags))

        assertTrue(KugouLyricsService.isSafePlainLyrics("[00:10.00] Normal LRC line"))
        assertFalse(KugouLyricsService.isSafePlainLyrics("encrypted\u0000binary\uFFFDblob"))
        assertFalse(KugouLyricsService.isSafePlainLyrics("A1B2C3D4".repeat(12)))
    }

    @Test
    fun testQQMusicLyricsService_rejectsEncryptedHexOrBinaryBlobs() {
        assertTrue(QQMusicLyricsService.isSafePlainLyrics("[00:10.00] 晴天\n[00:15.00] 故事的小黄花"))
        assertTrue(QQMusicLyricsService.isSafePlainLyrics("[10000,2000]故(10000,400)事(10400,500)"))

        assertFalse(QQMusicLyricsService.isSafePlainLyrics(""))
        assertFalse(QQMusicLyricsService.isSafePlainLyrics("DEADBEEF01234567".repeat(8)))
        assertFalse(QQMusicLyricsService.isSafePlainLyrics("binary\u0000\u0007data"))
        assertFalse(QQMusicLyricsService.isSafePlainLyrics("badutf8\uFFFDchars"))
    }

    private fun encodeSampleKrc(plainKrc: String): String {
        val input = plainKrc.toByteArray(Charsets.UTF_8)
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val deflatedOut = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            deflatedOut.write(buf, 0, count)
        }
        deflater.end()

        val key = byteArrayOf(
            0x40, 0x47, 0x61, 0x77,
            0x5E, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2D,
            0xCE.toByte(), 0xD2.toByte(), 0x6E, 0x69
        )
        val compressed = deflatedOut.toByteArray()
        val payload = ByteArray(4 + compressed.size)
        payload[0] = 'k'.code.toByte()
        payload[1] = 'r'.code.toByte()
        payload[2] = 'c'.code.toByte()
        payload[3] = '1'.code.toByte()
        for (i in compressed.indices) {
            payload[4 + i] = (compressed[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(payload)
    }

    @Test
    fun testAlignPreferredNetEaseLyrics_mergesYtlrcAndTlyricFallbackForSkippedLines() {
        val yrc = """
            [19430,4410](19430,390,0)Took (19820,90,0)a (19910,420,0)long (20330,1200,0)vacation
            [23840,4950](23840,240,0)You (24080,180,0)were (24260,510,0)balls (24770,330,0)deep
            [28790,4770](28790,240,0)So (29030,900,0)wasted
            [33590,4800](33590,750,0)Hurry (34340,360,0)now
        """.trimIndent()
        val ytlrcMissingExplicit = """
            [00:19.430]漫长的假期
            [00:33.590]抓紧时间
        """.trimIndent()
        val tlyricComplete = """
            [00:19.196]漫长的假期
            [00:24.049]你同我深入浅出
            [00:28.792]淋漓尽致
            [00:33.727]抓紧时间
        """.trimIndent()

        val alignedPair = NetEaseLyricsService.alignPreferredNetEaseLyrics(
            origLrc = "",
            transLrc = tlyricComplete,
            yrcLrc = yrc,
            ytLrc = ytlrcMissingExplicit
        )
        assertNotNull(alignedPair)
        val bilingual = LyricAligner.align(alignedPair!!.first, alignedPair.second)
        assertEquals(4, bilingual.size)
        assertEquals("漫长的假期", bilingual[0].translation)
        assertEquals("你同我深入浅出", bilingual[1].translation)
        assertEquals("淋漓尽致", bilingual[2].translation)
        assertEquals("抓紧时间", bilingual[3].translation)
    }

    @Test
    fun testExtractKrcLanguageTranslation_extractsType1AndSkipsDisclaimer() {
        val langJson = """
            {"content":[{"type":1,"language":0,"lyricContent":[["以下歌词翻译由文曲大模型提供"],["我发现一张秘密纸条"],["这种直觉将我淹没"]]}],"version":1}
        """.trimIndent()
        val langB64 = Base64.getEncoder().encodeToString(langJson.toByteArray(Charsets.UTF_8))
        val decodedKrc = """
            [ti:Stop The Wedding!]
            [language:$langB64]
            [246,815]<0,120,0>Stop <120,157,0>The <277,143,0>Wedding!
            [7880,3087]<0,197,0>I <197,165,0>found <362,190,0>a <552,378,0>secret <930,458,0>note
            [11259,3614]<0,182,0>It's <182,1232,0>supernatural
        """.trimIndent()

        val extracted = KugouLyricsService.extractKrcLanguageTranslation(decodedKrc)
        assertEquals(
            "[00:07.88] 我发现一张秘密纸条\n[00:11.25] 这种直觉将我淹没",
            extracted
        )
    }
}
