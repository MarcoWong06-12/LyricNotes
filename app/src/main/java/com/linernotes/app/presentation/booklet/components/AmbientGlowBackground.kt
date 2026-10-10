package com.linernotes.app.presentation.booklet.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
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
import com.linernotes.app.core.util.AlbumColorExtractor

/**
 * 现代流光弥散与动态流体极光背景 (Apple Music 风格 Dynamic Fluid Aurora Canvas)
 *
 * 1. 动态流体极光模式 (Dynamic Aurora)：
 *    - 极速异步提取封面主色调、副色调与氛围反差点缀色；
 *    - 4 粒子调和李萨如 (Lissajous) 曲线长周期缓慢流动与呼吸；
 *    - 切歌时如水墨晕染般优雅平滑交叉溶解 (1200ms LinearOutSlowInEasing)；
 * 2. 经典环境弥散模式 (Classic Glow)：
 *    - 保留原版双光斑低频柔和呼吸底色，供偏好低动态的用户自由选择；
 * 3. 纯黑 OLED 极暗模式 (AMOLED Pure Black)：
 *    - 深色下彻底关闭像素点发光 (0 nits)，停止一切动画与模糊计算，极致省电。
 */
@Composable
fun AmbientGlowBackground(
    coverUrl: String?,
    isDark: Boolean,
    isAmoledMode: Boolean = false,
    isDynamicAurora: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (isDark && isAmoledMode) {
        // 纯黑 OLED 极暗模式：彻底关闭像素点 (0 nits)，停止一切动画与模糊计算，极致省电
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    } else {
        val context = LocalContext.current
        val fallbackPrimary = MaterialTheme.colorScheme.primary
        val fallbackSecondary = MaterialTheme.colorScheme.secondary
        val fallbackTertiary = MaterialTheme.colorScheme.tertiaryContainer
        val backgroundColor = MaterialTheme.colorScheme.background

    // 动态提取专辑高质感色彩
    var extractedPalette by remember {
        mutableStateOf(
            AlbumColorExtractor.AuroraPalette(
                primary = fallbackPrimary,
                secondary = fallbackSecondary,
                tertiary = fallbackTertiary,
                accent = fallbackPrimary
            )
        )
    }

    LaunchedEffect(coverUrl, isDynamicAurora) {
        if (isDynamicAurora && !coverUrl.isNullOrBlank()) {
            extractedPalette = AlbumColorExtractor.extractColors(
                context = context,
                coverUrl = coverUrl,
                fallbackPrimary = fallbackPrimary,
                fallbackSecondary = fallbackSecondary,
                fallbackTertiary = fallbackTertiary
            )
        } else {
            extractedPalette = AlbumColorExtractor.AuroraPalette(
                primary = fallbackPrimary,
                secondary = fallbackSecondary,
                tertiary = fallbackTertiary,
                accent = fallbackPrimary
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")

    // 色彩平滑交叉溶解 (1200ms LinearOutSlowInEasing，切歌与主题切换如水墨晕染)
    val colorAnimSpec = tween<Color>(1200, easing = LinearOutSlowInEasing)
    val animatedPrimary by animateColorAsState(
        targetValue = if (isDynamicAurora) extractedPalette.primary else fallbackPrimary,
        animationSpec = colorAnimSpec,
        label = "ambPrimary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = if (isDynamicAurora) extractedPalette.secondary else fallbackSecondary,
        animationSpec = colorAnimSpec,
        label = "ambSecondary"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = if (isDynamicAurora) extractedPalette.tertiary else fallbackTertiary,
        animationSpec = colorAnimSpec,
        label = "ambTertiary"
    )
    val animatedAccent by animateColorAsState(
        targetValue = if (isDynamicAurora) extractedPalette.accent else fallbackPrimary,
        animationSpec = colorAnimSpec,
        label = "ambAccent"
    )
    val animatedBg by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = colorAnimSpec,
        label = "ambBg"
    )

    // 光斑 1: 主色调漂移与呼吸 (周期 9~12 秒)
    val orb1OffsetX by infiniteTransition.animateFloat(
        initialValue = -90f,
        targetValue = 110f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1X"
    )
    val orb1OffsetY by infiniteTransition.animateFloat(
        initialValue = -60f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1Y"
    )
    val orb1Scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1Scale"
    )

    // 光斑 2: 副色调反向漂移 (周期 11 秒)
    val orb2OffsetX by infiniteTransition.animateFloat(
        initialValue = 110f,
        targetValue = -100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2X"
    )
    val orb2OffsetY by infiniteTransition.animateFloat(
        initialValue = 90f,
        targetValue = -70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2Y"
    )

    // 动态流体极光特有：光斑 3 (底部深沉氛围) 与 光斑 4 (中部空灵高光)
    val orb3OffsetX by infiniteTransition.animateFloat(
        initialValue = -70f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3X"
    )
    val orb3OffsetY by infiniteTransition.animateFloat(
        initialValue = 50f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3Y"
    )

    val orb4OffsetX by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = -60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb4X"
    )
    val orb4OffsetY by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb4Y"
    )

    val effectiveDark = isDark || isDynamicAurora
    val orb1Colors = remember(animatedPrimary, animatedTertiary, effectiveDark) {
        listOf(
            animatedPrimary.copy(alpha = if (effectiveDark) 0.42f else 0.24f),
            animatedTertiary.copy(alpha = if (effectiveDark) 0.22f else 0.12f),
            Color.Transparent
        )
    }

    val orb2Colors = remember(animatedSecondary, animatedPrimary, effectiveDark) {
        listOf(
            animatedSecondary.copy(alpha = if (effectiveDark) 0.38f else 0.20f),
            animatedPrimary.copy(alpha = if (effectiveDark) 0.18f else 0.08f),
            Color.Transparent
        )
    }

    val orb3Colors = remember(animatedTertiary, animatedSecondary, effectiveDark) {
        listOf(
            animatedTertiary.copy(alpha = if (effectiveDark) 0.35f else 0.18f),
            animatedSecondary.copy(alpha = if (effectiveDark) 0.16f else 0.06f),
            Color.Transparent
        )
    }

    val orb4Colors = remember(animatedAccent, animatedPrimary, effectiveDark) {
        listOf(
            animatedAccent.copy(alpha = if (effectiveDark) 0.30f else 0.15f),
            animatedPrimary.copy(alpha = if (effectiveDark) 0.12f else 0.05f),
            Color.Transparent
        )
    }

    val overlayColors = remember(effectiveDark, animatedBg) {
        if (effectiveDark) {
            listOf(
                Color(0xFF0C0D12).copy(alpha = 0.48f),
                Color(0xFF0C0D12).copy(alpha = 0.26f),
                Color(0xFF090A0E).copy(alpha = 0.68f),
                Color(0xFF090A0E)
            )
        } else {
            listOf(
                animatedBg.copy(alpha = 0.72f),
                animatedBg.copy(alpha = 0.42f),
                animatedBg.copy(alpha = 0.80f),
                animatedBg
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 底层：唱片封面大半径高斯模糊 (放大 1.45 倍杜绝边缘白边)
        if (!coverUrl.isNullOrBlank()) {
            val imageRequest = remember(coverUrl) {
                ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (effectiveDark) (if (isDynamicAurora) 0.55f else 0.65f) else (if (isDynamicAurora) 0.40f else 0.45f)
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

        // 2. 动态光斑流体弥散层与纵深保护蒙版 (全部延迟至 GPU Draw Phase 绘制，重组率降为 0)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height

                    // 光斑 1 (主色调)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = orb1Colors,
                            center = Offset(w * 0.28f + orb1OffsetX, h * 0.25f + orb1OffsetY),
                            radius = (w * 0.75f) * orb1Scale
                        ),
                        alpha = if (isDark) 0.46f else 0.32f
                    )

                    // 光斑 2 (副色调)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = orb2Colors,
                            center = Offset(w * 0.72f + orb2OffsetX, h * 0.65f + orb2OffsetY),
                            radius = w * 0.82f
                        ),
                        alpha = if (isDark) 0.42f else 0.28f
                    )

                    // 仅在动态流体极光模式下激活光斑 3 与光斑 4，交织为高阶极光网格
                    if (isDynamicAurora) {
                        // 光斑 3 (深沉氛围)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = orb3Colors,
                                center = Offset(w * 0.45f + orb3OffsetX, h * 0.82f + orb3OffsetY),
                                radius = w * 0.78f
                            ),
                            alpha = if (isDark) 0.38f else 0.22f
                        )

                        // 光斑 4 (空灵浮动点缀)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = orb4Colors,
                                center = Offset(w * 0.60f + orb4OffsetX, h * 0.35f + orb4OffsetY),
                                radius = w * 0.60f
                            ),
                            alpha = if (isDark) 0.32f else 0.18f
                        )
                    }

                    // 纵深渐变遮罩：顶部保护状态栏/标题对比度，中部通透，底部深沉融合
                    drawRect(
                        brush = Brush.verticalGradient(colors = overlayColors)
                    )
                }
        )
    }
}
}
