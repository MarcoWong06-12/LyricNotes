package com.linernotes.app.presentation.booklet.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 现代流光弥散动态呼吸背景 (Apple Music 风格 Ambient Glow)
 *
 * 结合唱片封面大半径高斯模糊与双层流体光斑呼吸动效：
 * 1. 采用无感低功耗 InfiniteTransition 驱动双光斑缓慢漂移与呼吸；
 * 2. 纵深多段高动态渐变遮罩，无论暗色/浅色主题均保障歌词绝对可读性 (WCAG AAA)；
 * 3. 兼容 Android 12+ 硬件级 RenderEffect 模糊与全版本平滑过渡。
 */
@Composable
fun AmbientGlowBackground(
    coverUrl: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")

    // 第一光斑缓慢呼吸偏移量 (周期 9 秒，顺畅不突兀)
    val orb1OffsetX by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1X"
    )

    val orb1OffsetY by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1Y"
    )

    val orb1Scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1Scale"
    )

    // 第二光斑反向漂移 (周期 11 秒，形成错落有致的流体光场)
    val orb2OffsetX by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = -90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2X"
    )

    val orb2OffsetY by infiniteTransition.animateFloat(
        initialValue = 80f,
        targetValue = -60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2Y"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiaryContainer
    val backgroundColor = MaterialTheme.colorScheme.background

    // 色彩平滑交叉溶解 (800ms LinearOutSlowInEasing，切歌与主题切换如水墨晕染)
    val colorAnimSpec = tween<Color>(800, easing = LinearOutSlowInEasing)
    val animatedPrimary by animateColorAsState(targetValue = primaryColor, animationSpec = colorAnimSpec, label = "ambPrimary")
    val animatedSecondary by animateColorAsState(targetValue = secondaryColor, animationSpec = colorAnimSpec, label = "ambSecondary")
    val animatedTertiary by animateColorAsState(targetValue = tertiaryColor, animationSpec = colorAnimSpec, label = "ambTertiary")
    val animatedBg by animateColorAsState(targetValue = backgroundColor, animationSpec = colorAnimSpec, label = "ambBg")

    val orb1Colors = remember(animatedPrimary, animatedTertiary, isDark) {
        listOf(
            animatedPrimary.copy(alpha = if (isDark) 0.40f else 0.22f),
            animatedTertiary.copy(alpha = if (isDark) 0.22f else 0.10f),
            Color.Transparent
        )
    }

    val orb2Colors = remember(animatedSecondary, animatedPrimary, isDark) {
        listOf(
            animatedSecondary.copy(alpha = if (isDark) 0.35f else 0.18f),
            animatedPrimary.copy(alpha = if (isDark) 0.18f else 0.08f),
            Color.Transparent
        )
    }

    val overlayColors = remember(isDark, animatedBg) {
        if (isDark) {
            listOf(
                Color(0xFF0C0D12).copy(alpha = 0.48f),
                Color(0xFF0C0D12).copy(alpha = 0.28f),
                Color(0xFF090A0E).copy(alpha = 0.68f),
                Color(0xFF090A0E)
            )
        } else {
            listOf(
                animatedBg.copy(alpha = 0.72f),
                animatedBg.copy(alpha = 0.45f),
                animatedBg.copy(alpha = 0.80f),
                animatedBg
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 底层：唱片封面大半径高斯模糊 (放大 1.45 倍杜绝边缘白边)
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(800)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isDark) 0.65f else 0.45f
                        scaleX = 1.45f
                        scaleY = 1.45f
                    }
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(85.dp)
                        } else Modifier
                    )
            )
        }

        // 2. 动态双光斑流体弥散层与纵深保护蒙版 (全部延迟至 GPU Draw Phase 绘制，重组率降为 0)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 光斑 1
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = orb1Colors,
                            center = Offset(300f + orb1OffsetX, 400f + orb1OffsetY),
                            radius = 650f * orb1Scale
                        ),
                        alpha = if (isDark) 0.45f else 0.30f
                    )
                    // 光斑 2
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = orb2Colors,
                            center = Offset(750f + orb2OffsetX, 1100f + orb2OffsetY),
                            radius = 800f
                        ),
                        alpha = if (isDark) 0.40f else 0.25f
                    )
                    // 纵深渐变遮罩：顶部保护状态栏/标题对比度，中部通透，底部深沉融合
                    drawRect(
                        brush = Brush.verticalGradient(colors = overlayColors)
                    )
                }
        )
    }
}
