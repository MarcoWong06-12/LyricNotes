package com.linernotes.app.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 共享 Apple 风格弹性滑动分段控制器。
 * 支持 Float epsilon 相等性判定、弹簧滑块轨迹与轻量触觉反馈。
 */
@Composable
fun <T> AppleSegmentedControl(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    height: Dp = 34.dp,
    fontSize: TextUnit = 12.sp
) {
    if (items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val trackBg = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.065f)
    val thumbBg = if (isDark) Color(0xFF3A3A3C) else Color.White
    val thumbBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.62f) else Color(0xFF1C1C1E).copy(alpha = 0.58f)
    val thumbShape = RoundedCornerShape(8.dp)

    val selectedIndex = items.indexOfFirst { itemsEqual(it.first, selectedItem) }

    Surface(
        color = trackBg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.5.dp)
        ) {
            val segmentGap = 2.dp
            val totalGap = segmentGap * (items.size - 1).coerceAtLeast(0)
            val segmentWidth = ((maxWidth - totalGap) / items.size).coerceAtLeast(0.dp)

            if (selectedIndex >= 0 && segmentWidth > 0.dp) {
                val density = LocalDensity.current
                val targetOffsetPx = with(density) { ((segmentWidth + segmentGap) * selectedIndex).toPx() }
                val thumbOffsetPx by animateFloatAsState(
                    targetValue = targetOffsetPx,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 560f),
                    label = "segThumbOffsetPx"
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = thumbOffsetPx
                        }
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .then(
                            if (!isDark) {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = thumbShape,
                                    ambientColor = Color.Black.copy(alpha = 0.08f),
                                    spotColor = Color.Black.copy(alpha = 0.12f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(thumbShape)
                        .background(thumbBg)
                        .border(0.5.dp, thumbBorder, thumbShape)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(segmentGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, (itemVal, label) ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) textPrimary else textSecondary,
                        animationSpec = spring(stiffness = 650f),
                        label = "segText"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(thumbShape)
                            .bouncyClickable(pressedScale = 0.96f) {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onItemSelected(itemVal)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = fontSize,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun <T> itemsEqual(a: T, b: T): Boolean =
    if (a is Float && b is Float) abs(a - b) < 0.005f else a == b
