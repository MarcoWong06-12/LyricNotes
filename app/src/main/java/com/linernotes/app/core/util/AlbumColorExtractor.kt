package com.linernotes.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 专辑封面高动态色彩萃取器 (Apple Music 风格动态流体极光色板生成)
 *
 * 采用 24x24 极速低能耗采样与 HSL 饱和度色彩聚类算法：
 * 1. 自动过滤死黑与过曝高光，精准提取封面中最鲜活的主调、副调与氛围反差点缀色；
 * 2. 具备内存 LRU 记忆缓存，切歌与滑入滑出零重复 IO 与零重组损耗；
 * 3. 驱动 4 粒子流体极光网格 (Fluid Aurora Mesh) 呈现如水墨晕染般的呼吸舞台光场。
 */
object AlbumColorExtractor {

    data class AuroraPalette(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color,
        val accent: Color
    )

    private val cache = object : android.util.LruCache<String, AuroraPalette>(32) {}

    suspend fun extractColors(
        context: Context,
        coverUrl: String?,
        fallbackPrimary: Color,
        fallbackSecondary: Color,
        fallbackTertiary: Color
    ): AuroraPalette = withContext(Dispatchers.IO) {
        if (coverUrl.isNullOrBlank()) {
            return@withContext createFallback(fallbackPrimary, fallbackSecondary, fallbackTertiary)
        }

        cache.get(coverUrl)?.let { return@withContext it }

        try {
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(24, 24)
                .allowHardware(false)
                .build()

            val result = context.imageLoader.execute(request)
            val bitmap = when (val drawable = result.drawable) {
                is BitmapDrawable -> drawable.bitmap
                null -> null
                else -> {
                    try {
                        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, 24, 24)
                        drawable.draw(canvas)
                        bmp
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (bitmap == null || bitmap.isRecycled) {
                return@withContext createFallback(fallbackPrimary, fallbackSecondary, fallbackTertiary)
            }

            val colors = extractVibrantColorsFromBitmap(bitmap)
            val palette = if (colors.isNotEmpty()) {
                val p0 = colors[0]
                val p1 = if (colors.size > 1) colors[1] else shiftHue(p0, 35f)
                val p2 = if (colors.size > 2) colors[2] else shiftHue(p0, -35f)
                val p3 = if (colors.size > 3) colors[3] else shiftHue(p1, 45f)
                AuroraPalette(
                    primary = p0,
                    secondary = p1,
                    tertiary = p2,
                    accent = p3
                )
            } else {
                createFallback(fallbackPrimary, fallbackSecondary, fallbackTertiary)
            }

            cache.put(coverUrl, palette)
            palette
        } catch (e: Exception) {
            createFallback(fallbackPrimary, fallbackSecondary, fallbackTertiary)
        }
    }

    private fun extractVibrantColorsFromBitmap(bitmap: Bitmap): List<Color> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsl = FloatArray(3)
        val clusters = mutableMapOf<Int, MutableList<Int>>()
        val allValidPixels = mutableListOf<Int>()

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            android.graphics.Color.RGBToHSV(r, g, b, hsl)
            val hue = hsl[0]
            val sat = hsl[1]
            val value = hsl[2]

            // 过滤极暗死黑 (value < 0.08) 与极刺眼无彩纯白 (sat < 0.08 && value > 0.94)
            // 马卡龙粉红、明黄等高明度专辑色 (如 IGOR 的粉色：H=339, S=0.33, V=0.97) 必须完整保留！
            if (value < 0.08f) continue
            if (sat < 0.08f && value > 0.94f) continue

            allValidPixels.add(pixel)

            // 按色相 30 度聚类 (共 12 个主色区)
            if (sat >= 0.12f) {
                val hueBucket = (hue / 30f).toInt().coerceIn(0, 11)
                clusters.getOrPut(hueBucket) { mutableListOf() }.add(pixel)
            }
        }

        // 选出像素最多且平均饱和度最高的前几个色相聚类
        val sortedClusters = clusters.entries.sortedByDescending { it.value.size }
        val result = mutableListOf<Color>()

        for (entry in sortedClusters.take(4)) {
            val clusterPixels = entry.value
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (p in clusterPixels) {
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            val count = clusterPixels.size.coerceAtLeast(1)
            val avgColor = Color(
                red = (sumR / count).toInt(),
                green = (sumG / count).toInt(),
                blue = (sumB / count).toInt()
            )
            result.add(avgColor)
        }

        // 如果聚类为空 (例如纯黑白/灰阶唱片封面)，从有效像素中均匀抽样提取基础色
        if (result.isEmpty() && allValidPixels.isNotEmpty()) {
            val step = (allValidPixels.size / 4).coerceAtLeast(1)
            for (i in 0 until 4) {
                val idx = (i * step).coerceIn(0, allValidPixels.lastIndex)
                val p = allValidPixels[idx]
                result.add(
                    Color(
                        red = (p ushr 16) and 0xFF,
                        green = (p ushr 8) and 0xFF,
                        blue = p and 0xFF
                    )
                )
            }
        }

        return result
    }

    private fun shiftHue(color: Color, deltaHue: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
            hsv
        )
        hsv[0] = (hsv[0] + deltaHue + 360f) % 360f
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }

    private fun createFallback(
        primary: Color,
        secondary: Color,
        tertiary: Color
    ): AuroraPalette {
        return AuroraPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            accent = primary
        )
    }
}
