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

        title?.let { builder.append("[ti:$it]\n") }
        artist?.let { builder.append("[ar:$it]\n") }
        album?.let { builder.append("[al:$it]\n") }

        lines.forEach { line ->
            val timestamp = line.startTimeMs
            if (timestamp != null) {
                val adjustedTimestamp = (timestamp + offsetMs).coerceAtLeast(0L)
                val timeStr = formatTimestamp(adjustedTimestamp)
                
                builder.append("[$timeStr]${line.original}\n")
                if (line.translation.isNotBlank()) {
                    builder.append("[$timeStr]${line.translation}\n")
                }
            } else {
                builder.append("${line.original}\n")
                if (line.translation.isNotBlank()) {
                    builder.append("${line.translation}\n")
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
