package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.util.SpotifyLauncher
import com.linernotes.app.presentation.common.bouncyClickable
import com.linernotes.app.presentation.common.bouncyIconClickable
import com.linernotes.app.presentation.common.bouncyItemClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 待播队列 (Queue) 半屏抽屉 (流体物理弹簧动效与沉浸深色规范)
 * 1. 顶部：72% 黄金半屏高度限制，顶部露出现正播放专辑封面与暗光视差
 * 2. 标头：Queue + Playing {Artist} + 完成 药丸按钮
 * 3. 正在播放：纯净流体行，无臃肿外框，带动态跳动绿色均衡器声波 (Equalizer Wave)
 * 4. 待播队列：紧凑列表行，带 Explicit 徽章与三条杠拖拽手柄图标 (Drag Handle)
 * 5. 底栏：4 键专属沉浸播控坞 (Mix、Shuffle、Repeat、Timer 定时睡眠)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingQueueSheet(
    trackState: TrackPlaybackState,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToQueueItem: (QueueTrackItem) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 定时睡眠关闭状态管理
    var showTimerDialog by remember { mutableStateOf(false) }
    var timerRemainingMinutes by remember { mutableStateOf(0) }
    var isTimerActive by remember { mutableStateOf(false) }

    // 经典 Spotify 暗色系规范 (永驻沉浸深灰黑)
    val sheetBg = Color(0xFF121212)
    val cardBg = Color(0xFF1E1E1E)
    val buttonBg = Color(0xFF242424)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.60f)
    val spotifyGreen = Color(0xFF1ED760)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = Color.White.copy(alpha = 0.32f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.dp))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f) // 核心：严格限制半屏 (72% 黄金高度，露出顶部在播封面)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 1. 顶部标题栏 (Queue / Playing {Artist} / 完成)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Queue",
                        color = textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (trackState.artist.isNotBlank()) "Playing ${trackState.artist}" else "待播列表",
                        color = textSecondary,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = buttonBg,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.bouncyClickable(
                        pressedScale = 0.92f,
                        onClick = onDismiss
                    )
                ) {
                    Text(
                        text = "完成",
                        color = textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 正在播放 (Now playing) 标题
            Text(
                text = "正在播放 (Now playing)",
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // 正在播放单行 (无厚重外边框卡片，纯净 1:1 Spotify 极简风格)
            NowPlayingTrackRow(
                trackState = trackState,
                spotifyGreen = spotifyGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onPlayPause = onPlayPause
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 接下来播放 (Next in queue) 标题
            Text(
                text = "接下来播放 (Next in queue)",
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // 4. 队列列表 (权重 1f 弹性填充，保证顶部与底部控制坞恒定吸附)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (trackState.queueItems.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(trackState.queueItems) { item ->
                            SpotifyQueueItemRow(
                                item = item,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onClick = { onSkipToQueueItem(item) }
                            )
                        }
                    }
                } else {
                    // 当系统 MediaSession 未拿到完整队列时，展示官方沉浸说明卡片
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = spotifyGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "待播队列已就绪",
                                        color = textPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "已同步当前媒体播放会话。你可在此查看队列清单，或使用下方播控坞调节随机与循环模式。",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )

                                Surface(
                                    color = spotifyGreen,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .bouncyClickable(
                                            pressedScale = 0.96f,
                                            onClick = { SpotifyLauncher.launchSpotify(context) }
                                        )
                                 ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "管理播放列表 ➔",
                                            color = Color.Black,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. 底部 Spotify 专属 4 键播控坞 (Mix、Shuffle、Repeat、Timer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mix (音效调谐)
                SpotifyDockButton(
                    icon = Icons.Default.Tune,
                    label = "Mix",
                    isActive = false,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = { /* 扩展调音台 */ }
                )

                // 2. Shuffle (随机播放)
                SpotifyDockButton(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    isActive = trackState.isShuffleActive,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleShuffle
                )

                // 3. Repeat (循环模式)
                val repeatIcon = if (trackState.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
                val repeatLabel = when (trackState.repeatMode) {
                    1 -> "Repeat All"
                    2 -> "Repeat One"
                    else -> "Repeat"
                }
                SpotifyDockButton(
                    icon = repeatIcon,
                    label = repeatLabel,
                    isActive = trackState.repeatMode != 0,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = onCycleRepeat
                )

                // 4. Timer (定时关闭)
                val timerLabel = if (isTimerActive) "${timerRemainingMinutes}m" else "Timer"
                SpotifyDockButton(
                    icon = Icons.Default.Timer,
                    label = timerLabel,
                    isActive = isTimerActive,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = { showTimerDialog = true }
                )
            }
        }
    }

    // 定时关闭弹窗
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = {
                Text(
                    text = "定时停止播放",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val minutesOptions = listOf(15, 30, 45, 60)
                    minutesOptions.forEach { min ->
                        Surface(
                            color = if (isTimerActive && timerRemainingMinutes == min) spotifyGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(pressedScale = 0.96f) {
                                    isTimerActive = true
                                    timerRemainingMinutes = min
                                    showTimerDialog = false
                                    coroutineScope.launch {
                                        var left = min
                                        while (left > 0 && isTimerActive) {
                                            delay(60_000L)
                                            left--
                                            timerRemainingMinutes = left
                                        }
                                        if (isTimerActive) {
                                            isTimerActive = false
                                            if (trackState.isPlaying) onPlayPause()
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = "$min 分钟后",
                                color = if (isTimerActive && timerRemainingMinutes == min) spotifyGreen else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    if (isTimerActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(pressedScale = 0.96f) {
                                    isTimerActive = false
                                    timerRemainingMinutes = 0
                                    showTimerDialog = false
                                }
                        ) {
                            Text(
                                text = "关闭定时器",
                                color = Color(0xFFFF5252),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("取消", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

/**
 * 正在播放单曲行 (1:1 复刻 Spotify 界面)
 */
@Composable
private fun NowPlayingTrackRow(
    trackState: TrackPlaybackState,
    spotifyGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 封面 (46x46dp, 圆角 4dp)
        if (!trackState.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = trackState.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 歌曲信息 + 跳动绿色声波
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (trackState.isPlaying) {
                    PlayingEqualizerWave(color = spotifyGreen)
                }
                Text(
                    text = if (trackState.hasValidTrack) trackState.title else "LyricNotes",
                    color = spotifyGreen,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ExplicitBadge()
                Text(
                    text = if (trackState.hasValidTrack) trackState.artist else "等待播放",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 纯白圆形播放/暂停按钮 (36dp)
        Surface(
            color = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .bouncyIconClickable(
                    pressedScale = 0.88f,
                    onClick = onPlayPause
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (trackState.isPlaying) "暂停" else "播放",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 待播项行 (1:1 Spotify 极简行规范)
 */
@Composable
private fun SpotifyQueueItemRow(
    item: QueueTrackItem,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val displayTitle = if (item.title.isNotBlank()) {
        item.title
    } else if (item.album.isNotBlank() && item.album != item.artist) {
        item.album
    } else {
        "Unknown Track"
    }
    val displayArtist = if (item.artist.isNotBlank()) item.artist else item.album.ifBlank { "未知歌手" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .bouncyItemClickable(
                pressedScale = 0.98f,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 封面 (46x46dp, 圆角 4dp)
        if (!item.coverUri.isNullOrBlank()) {
            AsyncImage(
                model = item.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 标题与副标题
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = displayTitle,
                color = textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ExplicitBadge()
                Text(
                    text = displayArtist,
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 右侧三条杠拖拽手柄图标 (Spotify 标志性 ☰ 排序手柄)
        DragHandleBars(modifier = Modifier.padding(end = 4.dp))
    }
}

/**
 * 仿 Spotify 专属 3 条杠拖拽排序手柄图标 (☰)
 */
@Composable
private fun DragHandleBars(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.45f)
) {
    Column(
        modifier = modifier.width(18.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.8.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.fillMaxWidth().height(1.8.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.fillMaxWidth().height(1.8.dp).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

/**
 * Spotify 标志性 [E] Explicit 徽章
 */
@Composable
private fun ExplicitBadge() {
    Surface(
        color = Color.White.copy(alpha = 0.55f),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.size(width = 12.dp, height = 12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "E",
                color = Color.Black,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 8.5.sp
            )
        }
    }
}

/**
 * 绿色动态跳动均衡器声波 (Equalizer Wave)
 */
@Composable
private fun PlayingEqualizerWave(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1ED760)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqWave")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.90f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.40f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(2.2.dp).fillMaxHeight(bar1).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.width(2.2.dp).fillMaxHeight(bar2).clip(RoundedCornerShape(1.dp)).background(color))
        Box(modifier = Modifier.width(2.2.dp).fillMaxHeight(bar3).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

/**
 * Spotify 底部专属独立圆角方形按钮 (Screenshot 2: Mix, Shuffle, Repeat, Timer)
 */
@Composable
private fun SpotifyDockButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    spotifyGreen: Color,
    buttonBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (isActive) spotifyGreen else Color.White.copy(alpha = 0.70f)
    val containerBg = if (isActive) buttonBg else buttonBg

    Surface(
        color = containerBg,
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(1.dp, spotifyGreen.copy(alpha = 0.35f)) else null,
        modifier = modifier
            .height(52.dp)
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
    }
}
