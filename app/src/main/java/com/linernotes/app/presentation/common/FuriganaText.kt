package com.linernotes.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
    inactiveColor: Color = Color(0xFF9E9EA7),
    rubyColor: Color = Color.Unspecified,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start
) {
    val textColor = if (isActive) activeColor else inactiveColor
    val resolvedRubyColor = if (rubyColor != Color.Unspecified) {
        rubyColor
    } else {
        textColor.copy(alpha = if (isActive) 0.85f else 0.72f)
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.Bottom
    ) {
        segments.forEach { segment ->
            if (segment.reading != null && mode != FuriganaMode.OFF) {
                val rubyText = when (mode) {
                    FuriganaMode.ROMAJI -> FuriganaEngine.hiraganaToRomaji(segment.reading)
                    FuriganaMode.HIRAGANA -> segment.reading
                    else -> segment.reading
                }
                RubySegmentLayout(
                    rubyText = rubyText,
                    baseText = segment.text,
                    rubyTextStyle = rubyTextStyle,
                    baseTextStyle = baseTextStyle,
                    rubyColor = resolvedRubyColor,
                    baseColor = textColor,
                    modifier = Modifier.alignByBaseline()
                )
            } else {
                Text(
                    text = segment.text,
                    style = baseTextStyle,
                    color = textColor,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
    }
}

@Composable
private fun RubySegmentLayout(
    rubyText: String,
    baseText: String,
    rubyTextStyle: TextStyle,
    baseTextStyle: TextStyle,
    rubyColor: Color,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = rubyText,
                style = rubyTextStyle,
                color = rubyColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Text(
                text = baseText,
                style = baseTextStyle,
                color = baseColor
            )
        }
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val rubyPlaceable = measurables[0].measure(childConstraints)
        val basePlaceable = measurables[1].measure(childConstraints)
        val width = maxOf(rubyPlaceable.width, basePlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val rubyHeight = rubyPlaceable.height
        val totalHeight = (rubyHeight + basePlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)

        val baseFirst = basePlaceable[FirstBaseline]
        val baseLast = basePlaceable[LastBaseline]
        val alignmentLines = buildMap<AlignmentLine, Int> {
            if (baseFirst != AlignmentLine.Unspecified) {
                put(FirstBaseline, rubyHeight + baseFirst)
            }
            if (baseLast != AlignmentLine.Unspecified) {
                put(LastBaseline, rubyHeight + baseLast)
            }
        }

        layout(width, totalHeight, alignmentLines) {
            rubyPlaceable.placeRelative(x = (width - rubyPlaceable.width) / 2, y = 0)
            basePlaceable.placeRelative(x = (width - basePlaceable.width) / 2, y = rubyHeight)
        }
    }
}
