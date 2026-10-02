package com.linernotes.app.presentation.booklet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.linernotes.app.core.lyric.FuriganaEngine

enum class FuriganaMode { OFF, HIRAGANA, ROMAJI }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaText(
    segments: List<FuriganaEngine.FuriganaSegment>,
    mode: FuriganaMode,
    modifier: Modifier = Modifier,
    baseTextStyle: TextStyle = LocalTextStyle.current,
    rubyTextStyle: TextStyle = baseTextStyle.copy(fontSize = baseTextStyle.fontSize * 0.5f),
    isActive: Boolean = false,
    activeColor: Color = Color(0xFFEDEDED),
    inactiveColor: Color = Color(0xFF9E9EA7)
) {
    val textColor = if (isActive) activeColor else inactiveColor

    FlowRow(modifier = modifier) {
        segments.forEach { segment ->
            if (segment.reading != null && mode != FuriganaMode.OFF) {
                val rubyText = when (mode) {
                    FuriganaMode.ROMAJI -> FuriganaEngine.hiraganaToRomaji(segment.reading)
                    FuriganaMode.HIRAGANA -> segment.reading
                    else -> segment.reading
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rubyText, style = rubyTextStyle, color = textColor)
                    Text(segment.text, style = baseTextStyle, color = textColor)
                }
            } else {
                Text(segment.text, style = baseTextStyle, color = textColor)
            }
        }
    }
}
