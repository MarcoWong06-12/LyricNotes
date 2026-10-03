package com.linernotes.app.presentation.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 具有物理弹性触感的按压动效 Modifier (遵循 Apple WWDC & Emil Kowalski 交互物理学)
 * 1. 瞬时响应：手指触下瞬刻即刻发生微妙形变 (<100ms 响应，零迟滞)
 * 2. 严苛克制：形变控制在 0.94f~0.975f，杜绝夸张变形与闪烁透明度
 * 3. 次临界阻尼：dampingRatio 0.85f 保证回弹干净利落，无多余晃动
 */
fun Modifier.bouncyPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    pressedAlpha: Float = 0.94f,
    dampingRatio: Float = 0.85f,
    stiffness: Float = 900f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        ),
        label = "bouncyPressScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedAlpha else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = stiffness
        ),
        label = "bouncyPressAlpha"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * 通用交互弹性点击 Modifier (适用于按钮、胶囊、药丸控件)
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    pressedAlpha: Float = 0.94f,
    dampingRatio: Float = 0.84f,
    stiffness: Float = 880f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    this
        .bouncyPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            pressedAlpha = pressedAlpha,
            dampingRatio = dampingRatio,
            stiffness = stiffness
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null, // 由物理弹簧形变呈现高纯净度反馈
            enabled = enabled,
            onClick = {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (e: Exception) {
                    // 兼容静音或不支持触觉设备
                }
                onClick()
            }
        )
}

/**
 * 紧凑图标专用弹性点击 Modifier (适用于 32~44dp 圆形图标按键，形变稍深以提供清晰指尖触感)
 */
fun Modifier.bouncyIconClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.91f,
    onClick: () -> Unit
): Modifier = bouncyClickable(
    enabled = enabled,
    pressedScale = pressedScale,
    pressedAlpha = 0.92f,
    dampingRatio = 0.82f,
    stiffness = 920f,
    onClick = onClick
)

/**
 * 列表大卡片专用弹性点击 Modifier (适用于列表项、设置行、整卡展开，微缩放保持优雅)
 */
fun Modifier.bouncyItemClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    onClick: () -> Unit
): Modifier = bouncyClickable(
    enabled = enabled,
    pressedScale = pressedScale,
    pressedAlpha = 0.96f,
    dampingRatio = 0.86f,
    stiffness = 1000f,
    onClick = onClick
)

/**
 * 全局统一的丝滑弹性圆形图标按钮 (BouncyIconButton)
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    pressedScale: Float = 0.91f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.bouncyPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            dampingRatio = 0.84f,
            stiffness = 950f
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * 丝滑弹性实心按钮 (BouncyButton)
 */
@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedScale: Float = 0.95f,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.bouncyPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            dampingRatio = 0.85f,
            stiffness = 900f
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * 丝滑弹性次级色调按钮 (BouncyTonalButton)
 */
@Composable
fun BouncyTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedScale: Float = 0.95f,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.bouncyPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            dampingRatio = 0.85f,
            stiffness = 900f
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
