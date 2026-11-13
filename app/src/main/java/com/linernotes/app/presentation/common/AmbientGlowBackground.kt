package com.linernotes.app.presentation.common

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import coil.transform.Transformation
import com.linernotes.app.core.util.AlbumColorExtractor

private val SineEaseInOut = CubicBezierEasing(0.37f, 0.0f, 0.63f, 1.0f)

@Immutable
private data class AuroraRenderSnapshot(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val backgroundColor: Color,
    val effectiveDark: Boolean,
    val isDynamicAurora: Boolean
)

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

        val effectiveDark = isDark
        val targetSnapshot = remember(
            extractedPalette,
            fallbackPrimary,
            fallbackSecondary,
            fallbackTertiary,
            backgroundColor,
            effectiveDark,
            isDynamicAurora
        ) {
            AuroraRenderSnapshot(
                primary = if (isDynamicAurora) extractedPalette.primary else fallbackPrimary,
                secondary = if (isDynamicAurora) extractedPalette.secondary else fallbackSecondary,
                tertiary = if (isDynamicAurora) extractedPalette.tertiary else fallbackTertiary,
                accent = if (isDynamicAurora) extractedPalette.accent else fallbackPrimary,
                backgroundColor = backgroundColor,
                effectiveDark = effectiveDark,
                isDynamicAurora = isDynamicAurora
            )
        }

        // 色彩平滑交叉溶解：仅缓存稳态调色板与 Shader，切换时以单一 Animatable Alpha 在 GPU 层叠淡入，避免 120Hz 下每帧重建 Shader
        var previousSnapshot by remember { mutableStateOf<AuroraRenderSnapshot?>(null) }
        var currentSnapshot by remember { mutableStateOf(targetSnapshot) }
        val fadeProgress = remember { Animatable(1f) }

        LaunchedEffect(targetSnapshot) {
            if (targetSnapshot != currentSnapshot) {
                previousSnapshot = currentSnapshot
                currentSnapshot = targetSnapshot
                fadeProgress.snapTo(0f)
                fadeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1200, easing = LinearOutSlowInEasing)
                )
                previousSnapshot = null
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")

        // 光斑 1: 主色调漂移与正弦平缓呼吸 (消除 LinearEasing 折返尖点)
        val orb1OffsetX = infiniteTransition.animateFloat(
            initialValue = -90f,
            targetValue = 110f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1X"
        )
        val orb1OffsetY = infiniteTransition.animateFloat(
            initialValue = -60f,
            targetValue = 90f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1Y"
        )
        val orb1Scale = infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = SineEaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1Scale"
        )

        // 光斑 2: 副色调反向漂移 (周期 11 秒)
        val orb2OffsetX = infiniteTransition.animateFloat(
            initialValue = 110f,
            targetValue = -100f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb2X"
        )
        val orb2OffsetY = infiniteTransition.animateFloat(
            initialValue = 90f,
            targetValue = -70f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb2Y"
        )

        // 动态流体极光特有：关闭极光时不注册 orb3/orb4 无限动画，避免后台空转
        val zeroMotion = remember { mutableFloatStateOf(0f) }
        val runDynamicOrbs = isDynamicAurora || previousSnapshot?.isDynamicAurora == true
        val orb3OffsetX: State<Float>
        val orb3OffsetY: State<Float>
        val orb4OffsetX: State<Float>
        val orb4OffsetY: State<Float>
        if (runDynamicOrbs) {
            orb3OffsetX = infiniteTransition.animateFloat(
                initialValue = -70f,
                targetValue = 80f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 14000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "orb3X"
            )
            orb3OffsetY = infiniteTransition.animateFloat(
                initialValue = 50f,
                targetValue = -50f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 13000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "orb3Y"
            )
            orb4OffsetX = infiniteTransition.animateFloat(
                initialValue = 60f,
                targetValue = -60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "orb4X"
            )
            orb4OffsetY = infiniteTransition.animateFloat(
                initialValue = -80f,
                targetValue = 60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 9200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "orb4Y"
            )
        } else {
            orb3OffsetX = zeroMotion
            orb3OffsetY = zeroMotion
            orb4OffsetX = zeroMotion
            orb4OffsetY = zeroMotion
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (effectiveDark) Color(0xFF0E0E12) else backgroundColor)
        ) {
            // 1. 底层：唱片封面中等缩略采样与轻柔高斯感模糊（160x160 + 双轮 r=3 在保留柔和轮廓与极低开销间取得平衡）
            if (!coverUrl.isNullOrBlank()) {
                val imageRequest = remember(coverUrl) {
                    ImageRequest.Builder(context)
                        .data(coverUrl)
                        .size(160, 160)
                        .allowHardware(false)
                        .transformations(AuroraThumbnailBlurTransformation)
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
                            alpha = if (effectiveDark) {
                                if (isDynamicAurora) 0.90f else 0.82f
                            } else {
                                if (isDynamicAurora) 0.56f else 0.48f
                            }
                            scaleX = 1.15f
                            scaleY = 1.15f
                        }
                )
            }

            // 2. 动态光斑流体弥散层：稳态单层直出；切歌/切主题过渡时在 Draw 阶段按比例透明混合，零离屏 FBO 分配与 Shader 重建
            val prev = previousSnapshot
            if (prev != null) {
                AuroraOrbsLayer(
                    snapshot = prev,
                    orb1OffsetX = orb1OffsetX,
                    orb1OffsetY = orb1OffsetY,
                    orb1Scale = orb1Scale,
                    orb2OffsetX = orb2OffsetX,
                    orb2OffsetY = orb2OffsetY,
                    orb3OffsetX = orb3OffsetX,
                    orb3OffsetY = orb3OffsetY,
                    orb4OffsetX = orb4OffsetX,
                    orb4OffsetY = orb4OffsetY,
                    layerAlphaProvider = { (1f - fadeProgress.value).coerceIn(0f, 1f) },
                    drawOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
                AuroraOrbsLayer(
                    snapshot = currentSnapshot,
                    orb1OffsetX = orb1OffsetX,
                    orb1OffsetY = orb1OffsetY,
                    orb1Scale = orb1Scale,
                    orb2OffsetX = orb2OffsetX,
                    orb2OffsetY = orb2OffsetY,
                    orb3OffsetX = orb3OffsetX,
                    orb3OffsetY = orb3OffsetY,
                    orb4OffsetX = orb4OffsetX,
                    orb4OffsetY = orb4OffsetY,
                    layerAlphaProvider = { fadeProgress.value.coerceIn(0f, 1f) },
                    drawOverlay = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroraOrbsLayer(
                    snapshot = currentSnapshot,
                    orb1OffsetX = orb1OffsetX,
                    orb1OffsetY = orb1OffsetY,
                    orb1Scale = orb1Scale,
                    orb2OffsetX = orb2OffsetX,
                    orb2OffsetY = orb2OffsetY,
                    orb3OffsetX = orb3OffsetX,
                    orb3OffsetY = orb3OffsetY,
                    orb4OffsetX = orb4OffsetX,
                    orb4OffsetY = orb4OffsetY,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AuroraOrbsLayer(
    snapshot: AuroraRenderSnapshot,
    orb1OffsetX: State<Float>,
    orb1OffsetY: State<Float>,
    orb1Scale: State<Float>,
    orb2OffsetX: State<Float>,
    orb2OffsetY: State<Float>,
    orb3OffsetX: State<Float>,
    orb3OffsetY: State<Float>,
    orb4OffsetX: State<Float>,
    orb4OffsetY: State<Float>,
    layerAlphaProvider: () -> Float = { 1f },
    drawOverlay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val effectiveDark = snapshot.effectiveDark
    val orb1Colors = remember(snapshot.primary, snapshot.tertiary, effectiveDark) {
        listOf(
            snapshot.primary.copy(alpha = if (effectiveDark) 0.68f else 0.24f),
            snapshot.tertiary.copy(alpha = if (effectiveDark) 0.32f else 0.11f),
            Color.Transparent
        )
    }
    val orb2Colors = remember(snapshot.secondary, snapshot.primary, effectiveDark) {
        listOf(
            snapshot.secondary.copy(alpha = if (effectiveDark) 0.62f else 0.20f),
            snapshot.primary.copy(alpha = if (effectiveDark) 0.28f else 0.09f),
            Color.Transparent
        )
    }
    val orb3Colors = remember(snapshot.tertiary, snapshot.secondary, effectiveDark) {
        listOf(
            snapshot.tertiary.copy(alpha = if (effectiveDark) 0.55f else 0.18f),
            snapshot.secondary.copy(alpha = if (effectiveDark) 0.24f else 0.08f),
            Color.Transparent
        )
    }
    val orb4Colors = remember(snapshot.accent, snapshot.primary, effectiveDark) {
        listOf(
            snapshot.accent.copy(alpha = if (effectiveDark) 0.50f else 0.16f),
            snapshot.primary.copy(alpha = if (effectiveDark) 0.20f else 0.07f),
            Color.Transparent
        )
    }
    val overlayColors = remember(effectiveDark, snapshot.backgroundColor) {
        if (effectiveDark) {
            listOf(
                Color.Black.copy(alpha = 0.18f), // 顶部微暗角，保护状态栏与系统图标对比度
                Color.Transparent,               // 中部通透，让极光色彩自然流淌
                Color.Black.copy(alpha = 0.08f),
                Color.Black.copy(alpha = 0.22f)  // 底部柔和衬托，烘托悬浮播控胶囊
            )
        } else {
            val bg = snapshot.backgroundColor
            listOf(
                bg.copy(alpha = 0.36f),
                bg.copy(alpha = 0.20f),
                bg.copy(alpha = 0.24f),
                bg.copy(alpha = 0.38f)
            )
        }
    }

    Box(
        modifier = modifier.drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)

            val c1 = Offset(w * 0.28f, h * 0.25f)
            val c2 = Offset(w * 0.72f, h * 0.65f)
            val c3 = Offset(w * 0.45f, h * 0.82f)
            val c4 = Offset(w * 0.60f, h * 0.35f)

            // 仅在稳态调色板或画布尺寸改变时创建一次 Native Shader
            val brush1 = Brush.radialGradient(colors = orb1Colors, center = c1, radius = w * 0.75f)
            val brush2 = Brush.radialGradient(colors = orb2Colors, center = c2, radius = w * 0.82f)
            val brush3 = Brush.radialGradient(colors = orb3Colors, center = c3, radius = w * 0.78f)
            val brush4 = Brush.radialGradient(colors = orb4Colors, center = c4, radius = w * 0.60f)
            val verticalOverlayBrush = Brush.verticalGradient(colors = overlayColors)

            val bleed = 150f
            val expandedTopLeft = Offset(-bleed, -bleed)
            val expandedSize = Size(w + bleed * 2f, h + bleed * 2f)

            onDrawBehind {
                val layerAlpha = layerAlphaProvider().coerceIn(0f, 1f)
                if (layerAlpha > 0.001f) {
                    withTransform({
                        translate(left = orb1OffsetX.value, top = orb1OffsetY.value)
                        scale(scaleX = orb1Scale.value, scaleY = orb1Scale.value, pivot = c1)
                    }) {
                        drawRect(
                            brush = brush1,
                            topLeft = expandedTopLeft,
                            size = expandedSize,
                            alpha = (if (effectiveDark) 0.65f else 0.42f) * layerAlpha
                        )
                    }

                    withTransform({
                        translate(left = orb2OffsetX.value, top = orb2OffsetY.value)
                    }) {
                        drawRect(
                            brush = brush2,
                            topLeft = expandedTopLeft,
                            size = expandedSize,
                            alpha = (if (effectiveDark) 0.55f else 0.36f) * layerAlpha
                        )
                    }

                    if (snapshot.isDynamicAurora) {
                        withTransform({
                            translate(left = orb3OffsetX.value, top = orb3OffsetY.value)
                        }) {
                            drawRect(
                                brush = brush3,
                                topLeft = expandedTopLeft,
                                size = expandedSize,
                                alpha = (if (effectiveDark) 0.50f else 0.30f) * layerAlpha
                            )
                        }

                        withTransform({
                            translate(left = orb4OffsetX.value, top = orb4OffsetY.value)
                        }) {
                            drawRect(
                                brush = brush4,
                                topLeft = expandedTopLeft,
                                size = expandedSize,
                                alpha = (if (effectiveDark) 0.45f else 0.25f) * layerAlpha
                            )
                        }
                    }
                }

                if (drawOverlay) {
                    drawRect(brush = verticalOverlayBrush)
                }
            }
        }
    )
}

/**
 * 160x160 封面後台一次性雙輪小半徑 Box Blur（radius = 3, repeat = 2）：
 * 既消除主線程 / RenderThread 全螢幕模糊開銷，又保留清晰可辨的唱片構圖與色彩層次。
 */
private object AuroraThumbnailBlurTransformation : Transformation {
    override val cacheKey: String = "aurora_cover_blur_160_r3_p2"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val w = input.width
        val h = input.height
        if (w <= 1 || h <= 1) return input

        val readable = if (input.config == Bitmap.Config.ARGB_8888) {
            input
        } else {
            input.copy(Bitmap.Config.ARGB_8888, false)
        }
        val pixels = IntArray(w * h)
        readable.getPixels(pixels, 0, w, 0, 0, w, h)
        if (readable !== input) {
            readable.recycle()
        }

        val temp = IntArray(w * h)
        val radius = 3.coerceAtMost(minOf(w, h) / 2).coerceAtLeast(1)
        val div = radius * 2 + 1

        repeat(2) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOff = y * w
                var aSum = 0
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dx in -radius..radius) {
                    val c = pixels[rowOff + dx.coerceIn(0, w - 1)]
                    aSum += (c ushr 24) and 0xFF
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until w) {
                    temp[rowOff + x] = ((aSum / div) shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                    val remIdx = (x - radius).coerceIn(0, w - 1)
                    val addIdx = (x + radius + 1).coerceIn(0, w - 1)
                    val cRem = pixels[rowOff + remIdx]
                    val cAdd = pixels[rowOff + addIdx]
                    aSum += ((cAdd ushr 24) and 0xFF) - ((cRem ushr 24) and 0xFF)
                    rSum += ((cAdd ushr 16) and 0xFF) - ((cRem ushr 16) and 0xFF)
                    gSum += ((cAdd ushr 8) and 0xFF) - ((cRem ushr 8) and 0xFF)
                    bSum += (cAdd and 0xFF) - (cRem and 0xFF)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                var aSum = 0
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dy in -radius..radius) {
                    val c = temp[dy.coerceIn(0, h - 1) * w + x]
                    aSum += (c ushr 24) and 0xFF
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until h) {
                    pixels[y * w + x] = ((aSum / div) shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                    val remY = (y - radius).coerceIn(0, h - 1)
                    val addY = (y + radius + 1).coerceIn(0, h - 1)
                    val cRem = temp[remY * w + x]
                    val cAdd = temp[addY * w + x]
                    aSum += ((cAdd ushr 24) and 0xFF) - ((cRem ushr 24) and 0xFF)
                    rSum += ((cAdd ushr 16) and 0xFF) - ((cRem ushr 16) and 0xFF)
                    gSum += ((cAdd ushr 8) and 0xFF) - ((cRem ushr 8) and 0xFF)
                    bSum += (cAdd and 0xFF) - (cRem and 0xFF)
                }
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
