package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.util.SpotifyLauncher
import com.linernotes.app.presentation.common.*
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
    isAmoledMode: Boolean = false,
    sleepTimerMinutesLeft: Int = 0,
    onSetSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToQueueItem: (QueueTrackItem) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val dismissWithAnimation: () -> Unit = {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    // 定时睡眠关闭状态管理
    var showTimerDialog by remember { mutableStateOf(false) }
    val isTimerActive = sleepTimerMinutesLeft > 0

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isPureAmoled = isDark && isAmoledMode
    val sheetBg = when {
        isPureAmoled -> Color(0xFF0C0C0E)
        isDark -> Color(0xFF121212)
        else -> MaterialTheme.colorScheme.surface
    }
    val cardBg = when {
        isPureAmoled -> Color(0xFF141416)
        isDark -> Color(0xFF1E1E1E)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    val buttonBg = when {
        isPureAmoled -> Color(0xFF1C1C1F)
        isDark -> Color(0xFF242424)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderSubtle = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.20f)
    val textPrimary = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onSurfaceVariant
    val spotifyGreen = if (isDark) Color(0xFF1ED760) else Color(0xFF149E48)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.dp))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f) // 核心：严格限制半屏 (72% 黄金高度，露出顶部在播封面)
    ) {
        LockWindowRefreshRateEffect()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 1. 顶部标题栏 (队列名 / 歌手 / 完成)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.queueHeaderTitle,
                        color = textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (trackState.artist.isNotBlank()) {
                            String.format(strings.queuePlayingArtist, trackState.artist)
                        } else {
                            strings.queueTitle
                        },
                        color = textSecondary,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f),
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)),
                    modifier = Modifier.bouncyClickable(
                        pressedScale = 0.92f,
                        onClick = dismissWithAnimation
                    )
                ) {
                    Text(
                        text = strings.doneBtn,
                        color = textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 正在播放 标题
            Text(
                text = strings.nowPlayingPrefix,
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // 正在播放单行
            NowPlayingTrackRow(
                trackState = trackState,
                isDark = isDark,
                spotifyGreen = spotifyGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onPlayPause = onPlayPause
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 接下来播放 标题
            Text(
                text = strings.nextInQueue,
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
                        itemsIndexed(
                            items = trackState.queueItems,
                            key = { idx, item -> "${idx}_${item.id}_${item.title}" }
                        ) { _, item ->
                            SpotifyQueueItemRow(
                                item = item,
                                isDark = isDark,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onClick = { onSkipToQueueItem(item) }
                            )
                        }
                    }
                } else {
                    // 当系统 MediaSession 未拿到完整队列时，展示沉浸说明卡片
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = cardBg,
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(0.5.dp, borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
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
                                        text = strings.queueReadyTitle,
                                        color = textPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = strings.queueReadyDesc,
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )

                                Surface(
                                    color = spotifyGreen,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .bouncyClickable(
                                            pressedScale = 0.96f,
                                            onClick = { SpotifyLauncher.launchSpotify(context) }
                                        )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = strings.managePlaylistAction,
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

            // 5. 底部 4 键播控坞
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpotifyDockButton(
                    icon = Icons.Default.Tune,
                    label = "Mix",
                    isActive = false,
                    isDark = isDark,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = { /* 扩展调音台 */ }
                )

                SpotifyDockButton(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    isActive = trackState.isShuffleActive,
                    isDark = isDark,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleShuffle
                )

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
                    isDark = isDark,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = onCycleRepeat
                )

                val timerLabel = if (isTimerActive) "${sleepTimerMinutesLeft}m" else "Timer"
                SpotifyDockButton(
                    icon = Icons.Default.Timer,
                    label = timerLabel,
                    isActive = isTimerActive,
                    isDark = isDark,
                    spotifyGreen = spotifyGreen,
                    buttonBg = buttonBg,
                    modifier = Modifier.weight(1f),
                    onClick = { showTimerDialog = true }
                )
            }
        }
    }

    // 定时关闭弹窗（完整继承明暗与 AMOLED 主题及 120Hz 刷新率锁）
    if (showTimerDialog) {
        val dialogBg = if (isDark) (if (isPureAmoled) Color(0xFF0C0C0E) else Color(0xFF1E1E1E)) else MaterialTheme.colorScheme.surface
        val optionBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
        val optionBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(24.dp),
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = {
                LockWindowRefreshRateEffect()
                Text(
                    text = strings.sleepTimerTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val minutesOptions = listOf(15, 30, 45, 60)
                    minutesOptions.forEach { min ->
                        val isSelected = isTimerActive && sleepTimerMinutesLeft == min
                        Surface(
                            color = if (isSelected) spotifyGreen.copy(alpha = 0.18f) else optionBg,
                            shape = CircleShape,
                            border = BorderStroke(0.5.dp, if (isSelected) spotifyGreen.copy(alpha = 0.45f) else optionBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(pressedScale = 0.96f) {
                                    onSetSleepTimer(min)
                                    showTimerDialog = false
                                }
                        ) {
                            Text(
                                text = String.format(strings.minutesLater, min),
                                color = if (isSelected) spotifyGreen else textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }
                    }

                    if (isTimerActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(0.5.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(pressedScale = 0.96f) {
                                    onCancelSleepTimer()
                                    showTimerDialog = false
                                }
                        ) {
                            Text(
                                text = strings.sleepTimerOff,
                                color = Color(0xFFFF5252),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text(strings.cancel, color = textSecondary)
                }
            }
        )
    }
}

/**
 * 正在播放单曲行
 */
@Composable
private fun NowPlayingTrackRow(
    trackState: TrackPlaybackState,
    isDark: Boolean,
    spotifyGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    onPlayPause: () -> Unit
) {
    val strings = LocalStrings.current
    val borderTint = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!trackState.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = trackState.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, borderTint, RoundedCornerShape(8.dp))
            )
        } else {
            Surface(
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, borderTint),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

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
                    text = if (trackState.hasValidTrack) trackState.title else strings.appName,
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
                ExplicitBadge(isDark = isDark)
                Text(
                    text = if (trackState.hasValidTrack) trackState.artist else strings.waitingForPlaybackShort,
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            color = if (isDark) Color.White else Color(0xFF19191C),
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
                    contentDescription = if (trackState.isPlaying) strings.cdCompanionPause else strings.cdCompanionPlay,
                    tint = if (isDark) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 待播项行
 */
@Composable
private fun SpotifyQueueItemRow(
    item: QueueTrackItem,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    val borderTint = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val displayTitle = if (item.title.isNotBlank()) {
        item.title
    } else if (item.album.isNotBlank() && item.album != item.artist) {
        item.album
    } else {
        strings.unknownTrack
    }
    val displayArtist = if (item.artist.isNotBlank()) item.artist else item.album.ifBlank { strings.unknownArtist }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .bouncyItemClickable(
                pressedScale = 0.98f,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!item.coverUri.isNullOrBlank()) {
            AsyncImage(
                model = item.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, borderTint, RoundedCornerShape(8.dp))
            )
        } else {
            Surface(
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, borderTint),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

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
                ExplicitBadge(isDark = isDark)
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

        DragHandleBars(
            color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

/**
 * 3 条杠拖拽排序手柄图标
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
 * Explicit 徽章
 */
@Composable
private fun ExplicitBadge(isDark: Boolean = true) {
    Surface(
        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.size(width = 12.dp, height = 12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "E",
                color = if (isDark) Color.Black else Color.White,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 8.5.sp
            )
        }
    }
}

/**
 * 动态跳动均衡器声波（纯 GPU scaleY 缩放绘制，120Hz 零 Measure/Layout 分配）
 */
@Composable
private fun PlayingEqualizerWave(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1ED760)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqWave")
    val bar1 = infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 = infiniteTransition.animateFloat(
        initialValue = 0.90f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 = infiniteTransition.animateFloat(
        initialValue = 0.40f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(2.2.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = bar1.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.2.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = bar2.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.2.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = bar3.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

/**
 * 底部控制坞胶囊按钮
 */
@Composable
private fun SpotifyDockButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isDark: Boolean = true,
    spotifyGreen: Color,
    buttonBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isActive) spotifyGreen else if (isDark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = 500f),
        label = "dockBtnContent"
    )
    val containerBg by animateColorAsState(
        targetValue = if (isActive) spotifyGreen.copy(alpha = 0.16f) else buttonBg,
        animationSpec = spring(stiffness = 500f),
        label = "dockBtnBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) spotifyGreen.copy(alpha = 0.50f) else if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
        animationSpec = spring(stiffness = 500f),
        label = "dockBtnBorder"
    )

    Surface(
        color = containerBg,
        shape = CircleShape,
        border = BorderStroke(0.5.dp, borderColor),
        modifier = modifier
            .height(48.dp)
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
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
    }
}
