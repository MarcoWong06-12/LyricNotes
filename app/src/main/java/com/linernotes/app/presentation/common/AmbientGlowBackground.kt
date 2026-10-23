package com.linernotes.app.presentation.common

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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
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
                animatedPrimary.copy(alpha = if (effectiveDark) 0.68f else 0.45f),
                animatedTertiary.copy(alpha = if (effectiveDark) 0.32f else 0.18f),
                Color.Transparent
            )
        }

        val orb2Colors = remember(animatedSecondary, animatedPrimary, effectiveDark) {
            listOf(
                animatedSecondary.copy(alpha = if (effectiveDark) 0.62f else 0.40f),
                animatedPrimary.copy(alpha = if (effectiveDark) 0.28f else 0.14f),
                Color.Transparent
            )
        }

        val orb3Colors = remember(animatedTertiary, animatedSecondary, effectiveDark) {
            listOf(
                animatedTertiary.copy(alpha = if (effectiveDark) 0.55f else 0.35f),
                animatedSecondary.copy(alpha = if (effectiveDark) 0.24f else 0.12f),
                Color.Transparent
            )
        }

        val orb4Colors = remember(animatedAccent, animatedPrimary, effectiveDark) {
            listOf(
                animatedAccent.copy(alpha = if (effectiveDark) 0.50f else 0.30f),
                animatedPrimary.copy(alpha = if (effectiveDark) 0.20f else 0.10f),
                Color.Transparent
            )
        }

        val overlayColors = remember(effectiveDark, animatedBg) {
            if (effectiveDark) {
                listOf(
                    Color.Black.copy(alpha = 0.18f), // 顶部微暗角，保护状态栏与系统图标对比度
                    Color.Transparent,               // 中部 100% 纯净通透，让极光色彩畅快流淌呼吸
                    Color.Black.copy(alpha = 0.08f),
                    Color.Black.copy(alpha = 0.22f)  // 底部柔和衬托，烘托悬浮播控胶囊
                )
            } else {
                listOf(
                    animatedBg.copy(alpha = 0.30f),
                    animatedBg.copy(alpha = 0.10f),
                    animatedBg.copy(alpha = 0.20f),
                    animatedBg.copy(alpha = 0.32f)
                )
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            // 1. 底层：唱片封面微缩下采样与柔化 (微缩 72x72 已由 GPU 双线性过滤自然扩散，20dp 模糊替代 85dp 节省 16x 填充率)
            if (!coverUrl.isNullOrBlank()) {
                val imageRequest = remember(coverUrl) {
                    ImageRequest.Builder(context)
                        .data(coverUrl)
                        .size(72, 72)
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
                            alpha = if (effectiveDark) (if (isDynamicAurora) 0.88f else 0.78f) else (if (isDynamicAurora) 0.60f else 0.55f)
                            scaleX = 1.45f
                            scaleY = 1.45f
                        }
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(20.dp)
                            } else Modifier
                        )
                )
            }

            // 2. 动态光斑流体弥散层与纵深保护蒙版 (drawWithCache 预缓存静态 Shader + GPU 矩阵变换，每帧 0 对象分配)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val w = size.width.coerceAtLeast(1f)
                        val h = size.height.coerceAtLeast(1f)

                        val c1 = Offset(w * 0.28f, h * 0.25f)
                        val c2 = Offset(w * 0.72f, h * 0.65f)
                        val c3 = Offset(w * 0.45f, h * 0.82f)
                        val c4 = Offset(w * 0.60f, h * 0.35f)

                        // 仅在切歌换色或画布尺寸改变时创建一次 Native Shader
                        val brush1 = Brush.radialGradient(
                            colors = orb1Colors,
                            center = c1,
                            radius = w * 0.75f
                        )
                        val brush2 = Brush.radialGradient(
                            colors = orb2Colors,
                            center = c2,
                            radius = w * 0.82f
                        )
                        val brush3 = Brush.radialGradient(
                            colors = orb3Colors,
                            center = c3,
                            radius = w * 0.78f
                        )
                        val brush4 = Brush.radialGradient(
                            colors = orb4Colors,
                            center = c4,
                            radius = w * 0.60f
                        )
                        val verticalOverlayBrush = Brush.verticalGradient(colors = overlayColors)

                        // 预留 150px 边界冗余，防止光斑平移时矩形边缘在画布内露馅
                        val bleed = 150f
                        val expandedTopLeft = Offset(-bleed, -bleed)
                        val expandedSize = androidx.compose.ui.geometry.Size(w + bleed * 2f, h + bleed * 2f)

                        onDrawBehind {
                            // 光斑 1 (主色调：平移 + 呼吸缩放)
                            withTransform({
                                translate(left = orb1OffsetX, top = orb1OffsetY)
                                scale(scaleX = orb1Scale, scaleY = orb1Scale, pivot = c1)
                            }) {
                                drawRect(
                                    brush = brush1,
                                    topLeft = expandedTopLeft,
                                    size = expandedSize,
                                    alpha = if (effectiveDark) 0.65f else 0.40f
                                )
                            }

                            // 光斑 2 (副色调：平移)
                            withTransform({
                                translate(left = orb2OffsetX, top = orb2OffsetY)
                            }) {
                                drawRect(
                                    brush = brush2,
                                    topLeft = expandedTopLeft,
                                    size = expandedSize,
                                    alpha = if (effectiveDark) 0.55f else 0.35f
                                )
                            }

                            if (isDynamicAurora) {
                                // 光斑 3 (深沉氛围)
                                withTransform({
                                    translate(left = orb3OffsetX, top = orb3OffsetY)
                                }) {
                                    drawRect(
                                        brush = brush3,
                                        topLeft = expandedTopLeft,
                                        size = expandedSize,
                                        alpha = if (effectiveDark) 0.50f else 0.30f
                                    )
                                }

                                // 光斑 4 (空灵浮动点缀)
                                withTransform({
                                    translate(left = orb4OffsetX, top = orb4OffsetY)
                                }) {
                                    drawRect(
                                        brush = brush4,
                                        topLeft = expandedTopLeft,
                                        size = expandedSize,
                                        alpha = if (effectiveDark) 0.45f else 0.25f
                                    )
                                }
                            }

                            // 纵深渐变遮罩
                            drawRect(brush = verticalOverlayBrush)
                        }
                    }
            )
        }
    }
}
