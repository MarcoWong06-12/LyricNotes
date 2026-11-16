package com.linernotes.app.presentation.common

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import coil.transform.Transformation
import com.linernotes.app.core.util.AlbumColorExtractor
import kotlinx.coroutines.isActive

private fun lerpOsc(tSec: Float, halfPeriodSec: Float, start: Float, end: Float): Float {
    val phase = (0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * tSec / halfPeriodSec)).coerceIn(0f, 1f)
    return start + (end - start) * phase
}

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
    isPaused: Boolean = false,
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

        val runDynamicOrbs = isDynamicAurora || previousSnapshot?.isDynamicAurora == true
        var elapsedSeconds by remember { mutableFloatStateOf(0f) }
        val orb1OffsetX = remember { mutableFloatStateOf(-90f) }
        val orb1OffsetY = remember { mutableFloatStateOf(-60f) }
        val orb1Scale = remember { mutableFloatStateOf(0.92f) }
        val orb2OffsetX = remember { mutableFloatStateOf(110f) }
        val orb2OffsetY = remember { mutableFloatStateOf(90f) }
        val orb3OffsetX = remember { mutableFloatStateOf(0f) }
        val orb3OffsetY = remember { mutableFloatStateOf(0f) }
        val orb4OffsetX = remember { mutableFloatStateOf(0f) }
        val orb4OffsetY = remember { mutableFloatStateOf(0f) }

        LaunchedEffect(isPaused, runDynamicOrbs) {
            if (!isPaused) {
                var lastFrameNanos = 0L
                while (isActive) {
                    withFrameNanos { frameNanos ->
                        if (lastFrameNanos != 0L) {
                            val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                            elapsedSeconds += dt
                            val t = elapsedSeconds
                            orb1OffsetX.floatValue = lerpOsc(t, 9.6f, -90f, 110f)
                            orb1OffsetY.floatValue = lerpOsc(t, 8.4f, -60f, 90f)
                            orb1Scale.floatValue = lerpOsc(t, 8.0f, 0.92f, 1.18f)
                            orb2OffsetX.floatValue = lerpOsc(t, 11.2f, 110f, -100f)
                            orb2OffsetY.floatValue = lerpOsc(t, 10.4f, 90f, -70f)
                            if (runDynamicOrbs) {
                                orb3OffsetX.floatValue = lerpOsc(t, 14.0f, -70f, 80f)
                                orb3OffsetY.floatValue = lerpOsc(t, 13.0f, 50f, -50f)
                                orb4OffsetX.floatValue = lerpOsc(t, 10.0f, 60f, -60f)
                                orb4OffsetY.floatValue = lerpOsc(t, 9.2f, -80f, 60f)
                            }
                        }
                        lastFrameNanos = frameNanos
                    }
                }
            }
        }

        val currentAvgLuminance = remember(currentSnapshot.primary, currentSnapshot.secondary, currentSnapshot.tertiary) {
            (currentSnapshot.primary.luminance() + currentSnapshot.secondary.luminance() + currentSnapshot.tertiary.luminance()) / 3f
        }
        val brightCoverDim = if (effectiveDark) {
            ((currentAvgLuminance - 0.45f) / 0.55f).coerceIn(0f, 1f) * 0.28f
        } else {
            // 浅色模式下仅做轻微明度收敛，保留专辑封面的色彩饱和度与立体层次，避免像蒙了一层厚白布
            ((0.28f - currentAvgLuminance) / 0.28f).coerceIn(0f, 1f) * 0.16f
        }
        val lightTintedBase = remember(effectiveDark, backgroundColor, currentSnapshot.primary) {
            if (effectiveDark) Color(0xFF0E0E12) else lerp(backgroundColor, currentSnapshot.primary, 0.12f)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(lightTintedBase)
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
                            val baseAlpha = if (effectiveDark) {
                                if (isDynamicAurora) 0.90f else 0.82f
                            } else {
                                if (isDynamicAurora) 0.68f else 0.58f
                            }
                            alpha = (baseAlpha * (1f - brightCoverDim)).coerceIn(0.32f, 0.92f)
                            scaleX = 1.20f
                            scaleY = 1.20f
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
    // 浅色模式下为光斑注入通透粉彩光感，替代暗沉灰调或大面积白雾遮罩
    val lightOrbPrimary = remember(snapshot.primary, effectiveDark) {
        if (effectiveDark) snapshot.primary else lerp(snapshot.primary, Color.White, 0.22f)
    }
    val lightOrbSecondary = remember(snapshot.secondary, effectiveDark) {
        if (effectiveDark) snapshot.secondary else lerp(snapshot.secondary, Color.White, 0.24f)
    }
    val lightOrbTertiary = remember(snapshot.tertiary, effectiveDark) {
        if (effectiveDark) snapshot.tertiary else lerp(snapshot.tertiary, Color.White, 0.22f)
    }
    val lightOrbAccent = remember(snapshot.accent, effectiveDark) {
        if (effectiveDark) snapshot.accent else lerp(snapshot.accent, Color.White, 0.24f)
    }
    val orb1Colors = remember(lightOrbPrimary, lightOrbTertiary, effectiveDark) {
        listOf(
            lightOrbPrimary.copy(alpha = if (effectiveDark) 0.68f else 0.44f),
            lightOrbTertiary.copy(alpha = if (effectiveDark) 0.32f else 0.20f),
            Color.Transparent
        )
    }
    val orb2Colors = remember(lightOrbSecondary, lightOrbPrimary, effectiveDark) {
        listOf(
            lightOrbSecondary.copy(alpha = if (effectiveDark) 0.62f else 0.38f),
            lightOrbPrimary.copy(alpha = if (effectiveDark) 0.28f else 0.16f),
            Color.Transparent
        )
    }
    val orb3Colors = remember(lightOrbTertiary, lightOrbSecondary, effectiveDark) {
        listOf(
            lightOrbTertiary.copy(alpha = if (effectiveDark) 0.55f else 0.34f),
            lightOrbSecondary.copy(alpha = if (effectiveDark) 0.24f else 0.14f),
            Color.Transparent
        )
    }
    val orb4Colors = remember(lightOrbAccent, lightOrbPrimary, effectiveDark) {
        listOf(
            lightOrbAccent.copy(alpha = if (effectiveDark) 0.50f else 0.30f),
            lightOrbPrimary.copy(alpha = if (effectiveDark) 0.20f else 0.12f),
            Color.Transparent
        )
    }
    val overlayColorStops = remember(effectiveDark, snapshot.backgroundColor, snapshot.primary, snapshot.secondary, snapshot.tertiary) {
        val avgLuminance = (snapshot.primary.luminance() + snapshot.secondary.luminance() + snapshot.tertiary.luminance()) / 3f
        if (effectiveDark) {
            val brightBoost = ((avgLuminance - 0.42f) / 0.58f).coerceIn(0f, 1f)
            arrayOf(
                0.0f to Color.Black.copy(alpha = 0.24f + brightBoost * 0.30f),
                0.20f to Color.Black.copy(alpha = 0.08f + brightBoost * 0.16f),
                0.50f to Color.Black.copy(alpha = brightBoost * 0.12f),
                0.78f to Color.Black.copy(alpha = 0.12f + brightBoost * 0.16f),
                1.0f to Color.Black.copy(alpha = 0.28f + brightBoost * 0.30f)
            )
        } else {
            // 仅在顶栏/底栏边缘柔化压亮保证深色字迹清晰，中部主歌词区接近零白雾遮挡，呈现通透彩色磨砂玻璃质感
            val darkBoost = ((0.28f - avgLuminance) / 0.28f).coerceIn(0f, 1f)
            val tintVeil = lerp(snapshot.backgroundColor, snapshot.primary, 0.10f)
            arrayOf(
                0.0f to tintVeil.copy(alpha = 0.26f + darkBoost * 0.20f),
                0.18f to tintVeil.copy(alpha = 0.09f + darkBoost * 0.10f),
                0.50f to tintVeil.copy(alpha = 0.02f + darkBoost * 0.08f),
                0.82f to tintVeil.copy(alpha = 0.11f + darkBoost * 0.12f),
                1.0f to tintVeil.copy(alpha = 0.30f + darkBoost * 0.22f)
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
            val verticalOverlayBrush = Brush.verticalGradient(*overlayColorStops)

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
                            alpha = (if (effectiveDark) 0.65f else 0.62f) * layerAlpha
                        )
                    }

                    withTransform({
                        translate(left = orb2OffsetX.value, top = orb2OffsetY.value)
                    }) {
                        drawRect(
                            brush = brush2,
                            topLeft = expandedTopLeft,
                            size = expandedSize,
                            alpha = (if (effectiveDark) 0.55f else 0.56f) * layerAlpha
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
                                alpha = (if (effectiveDark) 0.50f else 0.50f) * layerAlpha
                            )
                        }

                        withTransform({
                            translate(left = orb4OffsetX.value, top = orb4OffsetY.value)
                        }) {
                            drawRect(
                                brush = brush4,
                                topLeft = expandedTopLeft,
                                size = expandedSize,
                                alpha = (if (effectiveDark) 0.45f else 0.44f) * layerAlpha
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
