package com.linernotes.app.core.lyric

import com.linernotes.app.domain.model.BilingualLyricLine
import org.junit.Assert.*
import org.junit.Test

class LrcExporterTest {

    @Test
    fun testGenerateLrcBasic() {
        val lines = listOf(
            BilingualLyricLine(1, "First line", "第一行", false, 1000L),
            BilingualLyricLine(2, "Second line", "第二行", false, 5500L)
        )
        val lrc = LrcExporter.generateLrc(
            lines = lines,
            offsetMs = 0L,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album"
        )

        assertTrue(lrc.contains("[ti:Test Song]"))
        assertTrue(lrc.contains("[ar:Test Artist]"))
        assertTrue(lrc.contains("[al:Test Album]"))
        assertTrue(lrc.contains("[00:01.00]First line"))
        assertTrue(lrc.contains("[00:01.00]第一行"))
        assertTrue(lrc.contains("[00:05.50]Second line"))
        assertTrue(lrc.contains("[00:05.50]第二行"))
    }

    @Test
    fun testGenerateLrcWithOffset() {
        val lines = listOf(
            BilingualLyricLine(1, "Original", "翻译", false, 2000L)
        )
        // Offset by +500ms
        val lrc = LrcExporter.generateLrc(lines, offsetMs = 500L)
        assertTrue(lrc.contains("[00:02.50]Original"))
        assertTrue(lrc.contains("[00:02.50]翻译"))

        // Offset by -1000ms
        val lrcMinus = LrcExporter.generateLrc(lines, offsetMs = -1000L)
        assertTrue(lrcMinus.contains("[00:01.00]Original"))
    }
}
