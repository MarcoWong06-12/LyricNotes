package com.linernotes.app.presentation.nowplaying.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.presentation.common.*
import com.linernotes.app.presentation.nowplaying.FuriganaDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricNotesSettingsSheet(
    trackState: TrackPlaybackState,
    hasSongStory: Boolean,
    geniusNotice: String? = null,
    isLoadingGenius: Boolean = false,
    furiganaMode: FuriganaDisplayMode,
    isTraditionalChinese: Boolean,
    isDeCensorEnabled: Boolean,
    showPlaybackControls: Boolean,
    lyricOffsetMs: Long,
    onOpenSongStory: () -> Unit,
    onReloadGenius: () -> Unit = {},
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSetFuriganaMode: (FuriganaDisplayMode) -> Unit,
    onToggleTraditionalChinese: () -> Unit,
    onToggleDeCensor: () -> Unit,
    onTogglePlaybackControls: () -> Unit,
    onReloadLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF13141B),
        contentColor = Color.White,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = Color.White.copy(alpha = 0.20f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.5.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部曲目与播放状态卡片
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!trackState.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = trackState.coverUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (trackState.hasValidTrack) trackState.title else "未检测到播放曲目",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (trackState.hasValidTrack) trackState.artist else "请在 Spotify 播放音乐",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (trackState.isSpotify) Color(0xFF1DB954).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        shape = CircleShape,
                        border = BorderStroke(0.5.dp, if (trackState.isSpotify) Color(0xFF1ED760).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.bouncyClickable(pressedScale = 0.92f) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                                context.startActivity(webIntent)
                            }
                        }
                    ) {
                        Text(
                            text = trackState.sourceApp.displayName,
                            color = if (trackState.isSpotify) Color(0xFF1ED760) else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. Genius 歌曲创作背景与典故状态
            if (hasSongStory) {
                Surface(
                    color = Color(0xFFFFD54F).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyItemClickable {
                            onDismiss()
                            onOpenSongStory()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💡", fontSize = 20.sp)
                            Column {
                                Text(
                                    text = "Genius 歌曲创作背景与乐评",
                                    color = Color(0xFFFFE082),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "查看作者创作灵感、访谈与深度文化隐喻",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFFFE082)
                        )
                    }
                }
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "geniusPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.12f,
                    targetValue = if (isLoadingGenius) 0.55f else 0.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "geniusBorderPulse"
                )

                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        0.5.dp,
                        if (isLoadingGenius) Color(0xFF1ED760).copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isLoadingGenius) "⏳" else "💡", fontSize = 18.sp)
                            Column {
                                Text(
                                    text = "Genius 典故与背景故事",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isLoadingGenius) "正在重新检索 Genius 典故与背景故事..." else (geniusNotice ?: "未检索到本曲 Genius 典故注释"),
                                    color = if (isLoadingGenius) Color(0xFF1ED760) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // 重试 / 检索中 胶囊按钮 (专为手指触控优化，高响应物理弹簧反馈)
                        Surface(
                            color = if (isLoadingGenius) Color(0xFF1ED760).copy(alpha = 0.12f) else Color(0xFF1ED760).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                0.5.dp,
                                if (isLoadingGenius) Color(0xFF1ED760).copy(alpha = 0.30f) else Color(0xFF1ED760).copy(alpha = 0.40f)
                            ),
                            modifier = Modifier
                                .bouncyClickable(
                                    enabled = !isLoadingGenius,
                                    pressedScale = 0.90f,
                                    onClick = onReloadGenius
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isLoadingGenius) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp,
                                        color = Color(0xFF1ED760)
                                    )
                                    Text(
                                        text = "检索中...",
                                        color = Color(0xFF1ED760),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF1ED760),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "重试",
                                        color = Color(0xFF1ED760),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 歌词时间轴微调与低延迟补偿 (Lyrics Offset)
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "歌词同步与时间轴补偿",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "抵消蓝牙/系统音频缓冲延迟，消除提前跳行感",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Text(
                            text = when (lyricOffsetMs) {
                                0L -> "0 ms (无补偿)"
                                -200L -> "-200 ms (推荐)"
                                else -> "${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs} ms"
                            },
                            color = if (lyricOffsetMs == -200L) Color(0xFF1ED760) else Color.White.copy(alpha = 0.85f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OffsetButton("-100ms", onClick = { onAdjustOffset(-100) }, modifier = Modifier.weight(1f))
                        OffsetButton("-50ms", onClick = { onAdjustOffset(-50) }, modifier = Modifier.weight(1f))
                        OffsetButton("推荐-200ms", onClick = onResetOffset, modifier = Modifier.weight(1.35f), isReset = true)
                        OffsetButton("+50ms", onClick = { onAdjustOffset(50) }, modifier = Modifier.weight(1f))
                        OffsetButton("+100ms", onClick = { onAdjustOffset(100) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // 4. 桌面悬浮歌词胶囊 (Floating Lyrics Capsule)
            val isFloatingActive by FloatingLyricsService.isRunningFlow.collectAsState()
            val isOverlayGranted = Settings.canDrawOverlays(context)
            val floatingPrefs = remember { FloatingLyricsPreferences.get(context) }
            val currentBgAlpha by floatingPrefs.backgroundAlphaFlow.collectAsState(initial = floatingPrefs.backgroundAlpha)
            val currentTextColor by floatingPrefs.textColorFlow.collectAsState(initial = floatingPrefs.textColor)
            val isLocked by floatingPrefs.isLockedFlow.collectAsState(initial = floatingPrefs.isLocked)
            val currentDisplayMode by floatingPrefs.displayModeFlow.collectAsState(initial = floatingPrefs.displayMode)
            val currentCapsuleWidth by floatingPrefs.capsuleWidthDpFlow.collectAsState(initial = floatingPrefs.capsuleWidthDp)
            val currentFloatingStyle by floatingPrefs.floatingStyleFlow.collectAsState(initial = floatingPrefs.floatingStyle)

            Surface(
                color = if (isFloatingActive) Color(0xFF162538).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    0.5.dp,
                    if (isFloatingActive) Color(0xFF81D4FA).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.10f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🫧", fontSize = 19.sp)
                            Column {
                                Text(
                                    text = "桌面悬浮歌词",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = if (!isOverlayGranted) "需授予【显示在其他应用上层】权限" else "纯净无框无底色桌面歌词，轻触唤出快捷播控",
                                    color = if (!isOverlayGranted) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Switch(
                            checked = isFloatingActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (Settings.canDrawOverlays(context)) {
                                        FloatingLyricsService.start(context)
                                    } else {
                                        FloatingLyricsService.requestOverlayPermission(context)
                                    }
                                } else {
                                    FloatingLyricsService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF03A9F4),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }

                    // 悬浮胶囊个性化定制参数 (形态、透明度、字体颜色、锁定穿透)
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 4.0 悬浮歌词形态 (纯净桌面歌词 vs 卡片胶囊)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "悬浮歌词形态",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "纯净模式无气泡边框与封面，仅歌词悬浮；卡片模式带气泡底板与封面",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val styleOptions = listOf(
                                FloatingLyricsPreferences.STYLE_PURE_LYRICS to "纯净桌面歌词 (网易云)",
                                FloatingLyricsPreferences.STYLE_CAPSULE_CARD to "微型卡片胶囊"
                            )
                            styleOptions.forEach { (styleVal, label) ->
                                val isSelected = currentFloatingStyle == styleVal
                                Surface(
                                    color = if (isSelected) Color(0xFF03A9F4).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .bouncyClickable(pressedScale = 0.92f) {
                                            floatingPrefs.floatingStyle = styleVal
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.1 视窗背景不透明度调节 (0%, 30%, 60%, 85%, 100%)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "视窗背景不透明度",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "选择 0% 呈现纯粹悬浮歌词；高不透明度可彻底阻断图标透光重叠",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Text(
                                text = "${(currentBgAlpha * 100).toInt()}%",
                                color = Color(0xFF81D4FA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val alphaOptions = listOf(
                                0.00f to "0% 纯透",
                                0.30f to "30% 微透",
                                0.60f to "60% 磨砂",
                                0.85f to "85% 半黑",
                                1.00f to "100% 实黑"
                            )
                            alphaOptions.forEach { (alphaVal, label) ->
                                val isSelected = kotlin.math.abs(currentBgAlpha - alphaVal) < 0.08f
                                Surface(
                                    color = if (isSelected) Color(0xFF03A9F4).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .bouncyClickable(pressedScale = 0.92f) {
                                            floatingPrefs.backgroundAlpha = alphaVal
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.75f),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.2 长歌词展示样式 (网易云智能折行 vs 单行跑马灯滚动)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "长歌词排版样式",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "智能折行可免展开完整阅读原文与译文；跑马灯为单行平滑滚动",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modeOptions = listOf(
                                FloatingLyricsPreferences.DISPLAY_MODE_WRAP to "折行全显 (网易云)",
                                FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE to "单行跑马灯"
                            )
                            modeOptions.forEach { (modeVal, label) ->
                                val isSelected = currentDisplayMode == modeVal
                                Surface(
                                    color = if (isSelected) Color(0xFF03A9F4).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .bouncyClickable(pressedScale = 0.92f) {
                                            floatingPrefs.displayMode = modeVal
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.3 悬浮窗宽度设置 (320dp, 356dp, 376dp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "悬浮窗宽度",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "更宽的悬浮窗可容纳更完整的多语种歌词",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Text(
                                text = "${currentCapsuleWidth} dp",
                                color = Color(0xFF81D4FA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val widthOptions = listOf(
                                320 to "320dp 紧凑",
                                356 to "356dp 推荐",
                                376 to "376dp 满屏"
                            )
                            widthOptions.forEach { (widthVal, label) ->
                                val isSelected = currentCapsuleWidth == widthVal
                                Surface(
                                    color = if (isSelected) Color(0xFF03A9F4).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .bouncyClickable(pressedScale = 0.92f) {
                                            floatingPrefs.capsuleWidthDp = widthVal
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.75f),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.2 歌词文字颜色自定义调色盘
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "歌词文字颜色",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "智能反差描边阴影，选择深色文字自动切换浅色磨砂白底板",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        val colorPresets = remember {
                            listOf(
                                -1 to Color.White,                       // 纯白
                                0xFFFFD54F.toInt() to Color(0xFFFFD54F), // 暖金
                                0xFF1ED760.toInt() to Color(0xFF1ED760), // 荧光绿 (Spotify)
                                0xFF00E5FF.toInt() to Color(0xFF00E5FF), // 电光青
                                0xFFFF80AB.toInt() to Color(0xFFFF80AB), // 樱花粉
                                0xFFB388FF.toInt() to Color(0xFFB388FF), // 浅紫
                                0xFFFF9100.toInt() to Color(0xFFFF9100), // 暖橙
                                0xFF121212.toInt() to Color(0xFF121212)  // 曜黑 (带白色反差投影)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorPresets.forEach { (colorVal, colorCompose) ->
                                val isSelected = currentTextColor == colorVal
                                val isDarkSwatch = colorCompose.luminance() < 0.20f

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colorCompose)
                                        .border(
                                            width = if (isSelected) 2.5.dp else if (isDarkSwatch) 1.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF00E5FF) else if (isDarkSwatch) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                        .bouncyClickable(pressedScale = 0.85f) {
                                            floatingPrefs.textColor = colorVal
                                        }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (colorCompose.luminance() > 0.45f) Color.Black else Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.3 触摸穿透锁定开关
                    SettingsSwitchRow(
                        title = "锁定触摸穿透",
                        subtitle = "锁定后手势穿透到下方桌面或应用，避免滑动误触（可通过通知栏解锁）",
                        checked = isLocked,
                        onCheckedChange = {
                            floatingPrefs.isLocked = !isLocked
                        }
                    )
                }
            }

            // 5. 显示与排版设置
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // 底部播放控制器开关 (Lyricify 风格纯净沉浸模式)
                    SettingsSwitchRow(
                        title = "底部播放控制栏",
                        subtitle = "关闭以开启全屏纯净歌词模式（Lyricify 风格）",
                        checked = showPlaybackControls,
                        onCheckedChange = { onTogglePlaybackControls() }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // 歌词脏字脱敏与审查还原
                    SettingsSwitchRow(
                        title = "歌词脏字审查还原",
                        subtitle = "自动将国内源星号屏蔽词（如 f***）还原为艺术原貌",
                        checked = isDeCensorEnabled,
                        onCheckedChange = { onToggleDeCensor() }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // 繁简转换
                    SettingsSwitchRow(
                        title = "繁体中文显示",
                        subtitle = "将中文歌词与译文无损转为繁体中文",
                        checked = isTraditionalChinese,
                        onCheckedChange = { onToggleTraditionalChinese() }
                    )
                }
            }

            // 5. 日语假名注音 (Furigana) 模式选择
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "日语假名注音（Furigana）",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FuriganaPill(
                            title = "关闭",
                            isSelected = furiganaMode == FuriganaDisplayMode.OFF,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.OFF) },
                            modifier = Modifier.weight(1f)
                        )
                        FuriganaPill(
                            title = "平假名注音",
                            isSelected = furiganaMode == FuriganaDisplayMode.HIRAGANA,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.HIRAGANA) },
                            modifier = Modifier.weight(1f)
                        )
                        FuriganaPill(
                            title = "罗马音",
                            isSelected = furiganaMode == FuriganaDisplayMode.ROMAJI,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.ROMAJI) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. 快捷操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .bouncyClickable(
                            pressedScale = 0.95f,
                            onClick = {
                                onDismiss()
                                onReloadLyrics()
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重载歌词", fontSize = 13.5.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }

                Surface(
                    color = Color(0xFF1DB954),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .bouncyClickable(
                            pressedScale = 0.95f,
                            onClick = {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                                    context.startActivity(webIntent)
                                }
                            }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("打开 Spotify", fontSize = 13.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isReset: Boolean = false
) {
    Surface(
        color = if (isReset) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, if (isReset) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f)),
        modifier = modifier
            .height(38.dp)
            .bouncyClickable(
                pressedScale = 0.90f,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isReset) Color.White else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = if (isReset) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bouncyItemClickable(pressedScale = 0.985f) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1DB954),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun FuriganaPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1ED760) else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(stiffness = 600f),
        label = "furiganaBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
        animationSpec = spring(stiffness = 600f),
        label = "furiganaText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1ED760) else Color.White.copy(alpha = 0.12f),
        animationSpec = spring(stiffness = 600f),
        label = "furiganaBorder"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, borderColor),
        modifier = modifier
            .height(38.dp)
            .bouncyClickable(
                pressedScale = 0.92f,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
