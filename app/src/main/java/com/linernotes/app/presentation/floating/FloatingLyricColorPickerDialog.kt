package com.linernotes.app.presentation.floating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.presentation.common.LockWindowRefreshRateEffect
import com.linernotes.app.presentation.common.bouncyClickable

/**
 * 桌面悬浮窗歌词颜色全功能自定义拾色弹窗
 * 提供实时桌面壁纸对照预览、HSV 多维色域平滑调节、十六进制 Hex 码精准输入与精选流行推荐色板。
 */
@Composable
fun FloatingLyricColorPickerDialog(
    currentColor: Int,
    isDark: Boolean,
    isPureAmoled: Boolean,
    isTraditional: Boolean = false,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val strings = LocalStrings.current
    val focusManager = LocalFocusManager.current

    // 初始化 HSV 状态
    val initialColorInt = if (currentColor != -1) currentColor else 0xFFFFFFFF.toInt()
    val initialHsv = remember(initialColorInt) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColorInt, array)
        array
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) } // 0..360
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) } // 0..1
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.20f)) } // 0.20..1

    // 当前计算得到的 Color Int 与 Compose Color
    val calculatedColorInt = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val currentComposeColor = remember(calculatedColorInt) {
        Color(calculatedColorInt)
    }

    // Hex 文本输入同步
    var hexInput by remember(calculatedColorInt) {
        mutableStateOf(String.format("%06X", 0xFFFFFF and calculatedColorInt))
    }

    // 预览壁纸模式：0-深色桌面, 1-浅色桌面, 2-极光炫彩桌面
    var previewBgMode by remember { mutableIntStateOf(0) }

    val dialogBg = if (isDark) {
        if (isPureAmoled) Color(0xFF0C0C0E) else Color(0xFF1C1C1E)
    } else Color.White

    val dialogBorder = if (isDark) {
        if (isPureAmoled) Color(0xFF222226) else Color.White.copy(alpha = 0.10f)
    } else Color.Black.copy(alpha = 0.08f)

    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f)
    val trackBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LockWindowRefreshRateEffect()

        Surface(
            color = dialogBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.5.dp, dialogBorder),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 顶部标题栏与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.floatingCustomColorTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontFamily = FontFamily.SansSerif
                    )

                    Surface(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable(pressedScale = 0.90f, onClick = onDismiss)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.closeBtn,
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 2. 桌面壁纸环境模拟悬浮歌词实时效果预览 (Live Capsule Preview)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.floatingCustomColorPreview,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )

                        // 壁纸底色微控制器
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bgOptions = listOf(
                                0 to (if (isTraditional) "深色" else "深色"),
                                1 to (if (isTraditional) "淺色" else "浅色"),
                                2 to (if (isTraditional) "極光" else "极光")
                            )
                            bgOptions.forEach { (mode, label) ->
                                val isSelected = previewBgMode == mode
                                Surface(
                                    color = if (isSelected) {
                                        if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)
                                    } else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isSelected) (if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f)) else Color.Transparent
                                    ),
                                    modifier = Modifier.bouncyClickable(pressedScale = 0.92f) { previewBgMode = mode }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) textPrimary else textSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 仿真桌面背景视窗
                    val previewBgBrush = when (previewBgMode) {
                        1 -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFFF3F4F6)))
                        2 -> Brush.linearGradient(
                            listOf(
                                Color(0xFF1E1B4B),
                                Color(0xFF312E81),
                                Color(0xFF065F46)
                            )
                        )
                        else -> Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF1F2937)))
                    }

                    val isLightPreviewColor = currentComposeColor.luminance() > 0.35f
                    val previewShadow = Shadow(
                        color = if (isLightPreviewColor) Color.Black.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.85f),
                        offset = Offset(0f, 1.2f),
                        blurRadius = 3f
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.5.dp, dialogBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(previewBgBrush)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "I said, ooh, I'm blinded by the lights",
                                    color = currentComposeColor,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    style = TextStyle(shadow = previewShadow)
                                )
                                Spacer(modifier = Modifier.height(2.5.dp))
                                Text(
                                    text = if (isTraditional) "我說，噢，我被這璀璨的燈光晃花了雙眼" else "我说，噢，我被这璀璨的灯光晃花了双眼",
                                    color = currentComposeColor.copy(alpha = if (isLightPreviewColor) 0.95f else 0.90f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    style = TextStyle(shadow = previewShadow)
                                )
                            }
                        }
                    }
                }

                // 3. Hex 十六进制色码与取色块
                Surface(
                    color = trackBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, dialogBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 颜色色块展示
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(currentComposeColor)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.floatingCustomColorHex,
                                fontSize = 11.sp,
                                color = textSecondary,
                                fontFamily = FontFamily.SansSerif
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                BasicTextField(
                                    value = hexInput,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                                        hexInput = clean
                                        if (clean.length == 6) {
                                            try {
                                                val parsedInt = (0xFF000000.toLong() or clean.toLong(16)).toInt()
                                                val hsvArr = FloatArray(3)
                                                android.graphics.Color.colorToHSV(parsedInt, hsvArr)
                                                hue = hsvArr[0]
                                                saturation = hsvArr[1]
                                                value = hsvArr[2].coerceAtLeast(0.20f)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    textStyle = TextStyle(
                                        color = textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(if (isDark) Color(0xFFFFD54F) else Color(0xFFD97706)),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 4. HSV 维度滑杆微调区
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 4.1 色相滑块 (Hue 0° ~ 360°)
                    val rainbowGradient = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0000FF),
                            Color(0xFFFF00FF),
                            Color(0xFFFF0000)
                        )
                    )
                    ColorSliderRow(
                        label = strings.floatingCustomColorHue,
                        valueLabel = "${hue.toInt()}°",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        sliderValue = hue,
                        valueRange = 0f..360f,
                        trackGradient = rainbowGradient,
                        onValueChange = {
                            hue = it
                            focusManager.clearFocus()
                        }
                    )

                    // 4.2 饱和度滑块 (Saturation 0% ~ 100%)
                    val satGradient = Brush.horizontalGradient(
                        listOf(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                        )
                    )
                    ColorSliderRow(
                        label = strings.floatingCustomColorSaturation,
                        valueLabel = "${(saturation * 100).toInt()}%",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        sliderValue = saturation,
                        valueRange = 0f..1f,
                        trackGradient = satGradient,
                        onValueChange = {
                            saturation = it
                            focusManager.clearFocus()
                        }
                    )

                    // 4.3 明度滑块 (Brightness 20% ~ 100%)
                    val valGradient = Brush.horizontalGradient(
                        listOf(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.20f))),
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                        )
                    )
                    ColorSliderRow(
                        label = strings.floatingCustomColorBrightness,
                        valueLabel = "${(value * 100).toInt()}%",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        sliderValue = value,
                        valueRange = 0.20f..1.0f,
                        trackGradient = valGradient,
                        onValueChange = {
                            value = it
                            focusManager.clearFocus()
                        }
                    )
                }

                // 5. 精选热门推荐预设色板 (Popular Swatches)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.floatingCustomColorPopular,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )

                    val popularSwatches = listOf(
                        0xFFFFFFFF.toInt(), // 纯净白
                        0xFFFFD54F.toInt(), // 经典暖金
                        0xFF1ED760.toInt(), // 流行荧光绿 (Spotify)
                        0xFF00E5FF.toInt(), // 赛博电光青
                        0xFF40C4FF.toInt(), // 澄澈冰川蓝
                        0xFFFF4081.toInt(), // 霓虹甜心粉
                        0xFFB388FF.toInt(), // 梦幻罗兰紫
                        0xFFFF9100.toInt(), // 晚霞暖阳橙
                        0xFF69F0AE.toInt(), // 清新薄荷绿
                        0xFFCFD8DC.toInt()  // 极简冷灰
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        popularSwatches.forEach { swatchColorInt ->
                            val isCurrentSelected = calculatedColorInt == swatchColorInt
                            val swatchCompose = Color(swatchColorInt)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(swatchCompose)
                                    .border(
                                        width = if (isCurrentSelected) 2.dp else 0.5.dp,
                                        color = if (isCurrentSelected) (if (isDark) Color.White else Color.Black) else Color.Black.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                                    .bouncyClickable(pressedScale = 0.88f) {
                                        val hsvArr = FloatArray(3)
                                        android.graphics.Color.colorToHSV(swatchColorInt, hsvArr)
                                        hue = hsvArr[0]
                                        saturation = hsvArr[1]
                                        value = hsvArr[2].coerceAtLeast(0.20f)
                                        focusManager.clearFocus()
                                    }
                            ) {
                                if (isCurrentSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (swatchCompose.luminance() > 0.45f) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    thickness = 0.5.dp
                )

                // 6. 底部操作按钮栏 (重置为白 | 取消 | 保存并应用)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            // 一键重置为纯白 #FFFFFF
                            hue = 0f
                            saturation = 0f
                            value = 1f
                            focusManager.clearFocus()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.floatingCustomColorReset,
                            color = textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, dialogBorder),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = strings.closeBtn,
                                fontSize = 13.sp,
                                color = textPrimary
                            )
                        }

                        Button(
                            onClick = {
                                onColorSelected(calculatedColorInt)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFD97706),
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = strings.applyBtn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    valueLabel: String,
    textPrimary: Color,
    textSecondary: Color,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trackGradient: Brush,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary
            )
            Text(
                text = valueLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景渐变色条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackGradient)
            )

            // Compose 原生无卡顿滑块，轨道设为透明，Thumb 浮在色条之上
            Slider(
                value = sliderValue,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
