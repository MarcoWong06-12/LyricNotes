package com.linernotes.app.presentation.nowplaying.components

import android.content.Intent
import android.net.Uri
import com.linernotes.app.core.util.SpotifyLauncher
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.presentation.common.*

/**
 * 仿 Spotify 待播队列 (Queue) 底部抽屉
 * 1. 顶部：正在播放曲目
 * 2. 中部：待播列表展示 / Spotify 系统媒体接口说明
 * 3. 底部：复刻 Spotify 专属控制栏 (Mix、Shuffle 随机、Repeat 循环、Timer 定时)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetBg = if (isDark) Color(0xFF13141B) else MaterialTheme.colorScheme.surface
    val sheetContent = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val cardBg = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    val handleColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.20f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = sheetContent,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.5.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // 1. 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Queue",
                        color = sheetContent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = if (trackState.isSpotify) "Spotify 同步待播队列" else "当前媒体播放队列",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.bouncyClickable(
                        pressedScale = 0.92f,
                        onClick = onDismiss
                    )
                ) {
                    Text(
                        text = "完成",
                        color = sheetContent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // 2. 正在播放 (Now playing)
            Text(
                text = "正在播放 (Now playing)",
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!trackState.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = trackState.coverUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (trackState.hasValidTrack) trackState.title else "LyricNotes",
                            color = Color(0xFF1ED760), // Spotify 生机绿高亮当前播放
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (trackState.hasValidTrack) trackState.artist else "等待播放",
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(38.dp)
                            .bouncyIconClickable(
                                pressedScale = 0.90f,
                                onClick = onPlayPause
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (trackState.isPlaying) "暂停" else "播放",
                                tint = Color(0xFF0F1118),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. 接下来播放 (Next in queue)
            Text(
                text = "接下来播放 (Next in queue)",
                color = textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (trackState.queueItems.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trackState.queueItems) { item ->
                        QueueItemRow(
                            item = item,
                            isDark = isDark,
                            onClick = { onSkipToQueueItem(item) }
                        )
                    }
                }
            } else {
                // 友好解析 Spotify 系统 MediaSession 机制
                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
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
                                tint = Color(0xFF1ED760),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Spotify 待播队列机制说明",
                                color = sheetContent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Text(
                            text = "Spotify 官方出于核心推荐流与云端保护，未向 Android 系统的媒体会话接口暴露待播队列数组。如需完整调取云端个性化队列，需通过 Spotify Web API 账号直连授权。您可以直接在 Spotify 中管理，或使用下方控制栏随时切换播放模式！",
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 18.sp
                        )

                        Surface(
                            color = Color(0xFF1DB954),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .bouncyClickable(
                                    pressedScale = 0.96f,
                                    onClick = {
                                        SpotifyLauncher.launchSpotify(context)
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "在 Spotify 查看完整待播队列 ➔",
                                    color = Color.Black,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. 底部 Spotify 专属控制栏 (复刻用户截屏 media_1790933607534.jpg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f),
                        RoundedCornerShape(20.dp)
                    )
                    .border(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mix (音效调控)
                SpotifyQueueBottomButton(
                    icon = Icons.Default.Tune,
                    label = "Mix",
                    isActive = false,
                    isDark = isDark,
                    onClick = { /* 扩展音效/风格 */ }
                )

                // 2. Shuffle (随机播放)
                SpotifyQueueBottomButton(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    isActive = trackState.isShuffleActive,
                    isDark = isDark,
                    onClick = onToggleShuffle
                )

                // 3. Repeat (循环播放: 关 -> 列表循环 -> 单曲循环)
                val repeatLabel = when (trackState.repeatMode) {
                    1 -> "Repeat All"
                    2 -> "Repeat One"
                    else -> "Repeat"
                }
                val repeatIcon = if (trackState.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat

                SpotifyQueueBottomButton(
                    icon = repeatIcon,
                    label = repeatLabel,
                    isActive = trackState.repeatMode != 0,
                    isDark = isDark,
                    onClick = onCycleRepeat
                )

                // 4. Timer (定时关闭)
                SpotifyQueueBottomButton(
                    icon = Icons.Default.Timer,
                    label = "Timer",
                    isActive = false,
                    isDark = isDark,
                    onClick = { /* 定时关闭设定 */ }
                )
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    item: QueueTrackItem,
    isDark: Boolean = true,
    onClick: () -> Unit = {}
) {
    val textPrimary = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    val placeholderBg = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
    val placeholderTint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)
    val playButtonBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val playAccent = if (isDark) Color(0xFF1ED760) else Color(0xFF1DB954)

    val displayTitle = if (item.title.isNotBlank()) {
        item.title
    } else if (item.album.isNotBlank() && item.album != item.artist) {
        item.album
    } else {
        "Unknown Track"
    }
    val displayArtist = if (item.artist.isNotBlank()) item.artist else item.album.ifBlank { "Spotify Queue" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bouncyItemClickable(
                pressedScale = 0.975f,
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Surface(
                color = placeholderBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = placeholderTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayArtist,
                color = textSecondary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = playButtonBg,
            shape = CircleShape,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放此曲",
                    tint = playAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun SpotifyQueueBottomButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isDark: Boolean = true,
    onClick: () -> Unit
) {
    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF1ED760) else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = if (isActive) Color(0xFF1ED760) else inactiveColor,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif
        )
    }
}
