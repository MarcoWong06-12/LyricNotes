package com.linernotes.app.core.lyric

import com.linernotes.app.domain.model.BilingualLyricLine
import java.util.Locale

object LrcExporter {

    fun generateLrc(
        lines: List<BilingualLyricLine>,
        offsetMs: Long = 0,
        title: String? = null,
        artist: String? = null,
        album: String? = null
    ): String {
        val builder = StringBuilder()

        title?.takeIf { it.isNotBlank() }?.let { builder.append("[ti:${it.trim()}]\n") }
        artist?.takeIf { it.isNotBlank() }?.let { builder.append("[ar:${it.trim()}]\n") }
        album?.takeIf { it.isNotBlank() }?.let { builder.append("[al:${it.trim()}]\n") }

        lines.forEach { line ->
            val cleanOriginal = line.original.replace('\r', ' ').replace('\n', ' ')
            val cleanTranslation = line.translation.replace('\r', ' ').replace('\n', ' ')
            val timestamp = line.startTimeMs
            if (timestamp != null) {
                val adjustedTimestamp = (timestamp + offsetMs).coerceAtLeast(0L)
                val timeStr = formatTimestamp(adjustedTimestamp)
                
                builder.append("[$timeStr]$cleanOriginal\n")
                if (cleanTranslation.isNotBlank()) {
                    builder.append("[$timeStr]$cleanTranslation\n")
                }
            } else {
                builder.append("$cleanOriginal\n")
                if (cleanTranslation.isNotBlank()) {
                    builder.append("$cleanTranslation\n")
                }
            }
        }
        
        return builder.toString()
    }

    private fun formatTimestamp(timeMs: Long): String {
        val minutes = (timeMs / 1000) / 60
        val seconds = (timeMs / 1000) % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}
