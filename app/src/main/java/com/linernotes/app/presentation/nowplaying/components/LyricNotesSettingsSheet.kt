package com.linernotes.app.presentation.nowplaying.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.linernotes.app.core.floating.FloatingLyricsService
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

            // 3. 歌词时间轴微调 (Lyrics Offset)
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
                        Text(
                            text = "歌词时间轴微调",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (lyricOffsetMs == 0L) "准时" else "${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs} ms",
                            color = if (lyricOffsetMs == 0L) Color.White.copy(alpha = 0.5f) else Color(0xFF1ED760),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OffsetButton("-0.5s", onClick = { onAdjustOffset(-500) }, modifier = Modifier.weight(1f))
                        OffsetButton("-0.2s", onClick = { onAdjustOffset(-200) }, modifier = Modifier.weight(1f))
                        OffsetButton("复位", onClick = onResetOffset, modifier = Modifier.weight(1f), isReset = true)
                        OffsetButton("+0.2s", onClick = { onAdjustOffset(200) }, modifier = Modifier.weight(1f))
                        OffsetButton("+0.5s", onClick = { onAdjustOffset(500) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // 4. 桌面悬浮歌词胶囊 (Floating Lyrics Capsule)
            val isFloatingActive by FloatingLyricsService.isRunningFlow.collectAsState()
            val isOverlayGranted = Settings.canDrawOverlays(context)

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
                                    text = "桌面悬浮歌词胶囊",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = if (!isOverlayGranted) "需授予【显示在其他应用上层】权限" else "轻触灵动胶囊展开播控，支持触摸穿透锁定",
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
