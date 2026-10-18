package com.linernotes.app.presentation.nowplaying.components

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.presentation.common.bouncyClickable
import com.linernotes.app.presentation.common.bouncyIconClickable

/**
 * 现代极简流体顶部导航栏
 * 左侧：极简毛玻璃折叠箭头 (KeyboardArrowDown)
 * 中间：副标题来源/状态优雅指示
 * 右侧：故事徽标 (✦ 故事) + 待播队列 + 更多设置
 */
@Composable
fun NowPlayingTopBar(
    trackState: TrackPlaybackState,
    hasSongStory: Boolean,
    isTraditional: Boolean,
    isDark: Boolean,
    onCoverClick: () -> Unit = {},
    onOpenSongStory: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleImmersive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pillBg = if (isDark) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)
    val pillIconTint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val strings = LocalStrings.current

    val context = LocalContext.current
    val isFloatingActive by FloatingLyricsService.isRunningFlow.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧与中心：折叠箭头 + 宽阔歌曲标题与歌手信息 (Apple Music 纯净高视线占比，杜绝截断省略)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Surface(
                color = pillBg,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, pillBorder),
                modifier = Modifier
                    .size(36.dp)
                    .bouncyIconClickable(onClick = onOpenSettings)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "收起/设置",
                        tint = pillIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (trackState.hasValidTrack) {
                // 1. 歌曲封面微缩图 (36dp 苹果级圆角微卡片，轻触放大查看大图)
                val coverScale by animateFloatAsState(
                    targetValue = if (trackState.hasValidTrack) 1.0f else 0.94f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
                    label = "topCoverScale"
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)),
                            RoundedCornerShape(10.dp)
                        )
                        .bouncyClickable(
                            pressedScale = 0.92f,
                            onClick = onCoverClick
                        )
                ) {
                    if (!trackState.coverUrl.isNullOrBlank()) {
                        val coverRequest = remember(trackState.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(trackState.coverUrl)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = coverRequest,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.50f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 歌名与歌手
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable(
                            pressedScale = 0.97f,
                            onClick = onOpenSettings
                        ),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val displayTitle = if (isTraditional) ChineseConverter.toTraditional(trackState.title) else trackState.title
                    val rawArtist = if (trackState.artist.isNotBlank()) trackState.artist else if (!trackState.album.isNullOrBlank()) trackState.album else ""
                    val displayArtist = if (isTraditional) ChineseConverter.toTraditional(rawArtist) else rawArtist
                    Text(
                        text = displayTitle,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 5000,
                            repeatDelayMillis = 3500,
                            velocity = 25.dp
                        )
                    )
                    Text(
                        text = displayArtist,
                        color = if (isDark) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = strings.appName,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // 右侧操作群：故事徽标 (✦ 故事) + 待播队列 (Queue) + 更多功能菜单 (...)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 歌曲背景故事外层入口 (纯净 36dp 晶体星芒圆钮，仅保留图标)
            AnimatedVisibility(
                visible = hasSongStory,
                enter = fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 420f), initialScale = 0.80f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.80f)
            ) {
                val storyBg = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f)
                val storyBorder = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f)
                val storyStarColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
                Surface(
                    color = storyBg,
                    shape = CircleShape,
                    border = BorderStroke(
                        0.5.dp,
                        storyBorder
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyIconClickable(
                            onClick = onOpenSongStory
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = strings.storyBadge,
                            tint = storyStarColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // 待播队列
            Surface(
                color = pillBg,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, pillBorder),
                modifier = Modifier
                    .size(36.dp)
                    .bouncyIconClickable(
                        onClick = onOpenQueue
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "待播队列",
                        tint = pillIconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 更多选项菜单 (Apple iOS 18 高圆角磨砂气泡卡片，消弭传统方角生硬感)
            Box {
                Surface(
                    color = pillBg,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, pillBorder),
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyIconClickable(
                            onClick = { showMoreMenu = true }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "更多选项",
                            tint = pillIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = if (isDark) Color(0xF2161924) else Color(0xF8F6F7FA),
                    shadowElevation = 18.dp,
                    border = BorderStroke(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isFloatingActive) "关闭悬浮歌词" else "桌面悬浮歌词",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = null,
                                tint = if (isFloatingActive) Color(0xFF0288D1) else pillIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            if (isFloatingActive) {
                                FloatingLyricsService.stop(context)
                            } else {
                                if (Settings.canDrawOverlays(context)) {
                                    FloatingLyricsService.start(context)
                                } else {
                                    FloatingLyricsService.requestOverlayPermission(context)
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .height(0.5.dp)
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "全屏沉浸模式",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint = pillIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onToggleImmersive()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .height(0.5.dp)
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "播放设置",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = pillIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onOpenSettings()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
            }
        }
    }
}
