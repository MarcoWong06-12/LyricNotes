package com.linernotes.app.presentation.floating

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.data.repository.NowPlayingData
import com.linernotes.app.presentation.common.bouncyIconClickable
import kotlinx.coroutines.delay

/**
 * Apple 灵动岛 / 桌面胶囊风格的悬浮歌词组件 (Floating Lyrics Capsule)
 * 支持极简胶囊态与拓展播控卡片态无缝弹簧形变
 */
@Composable
fun FloatingLyricsCapsule(
    nowPlayingData: NowPlayingData,
    isLocked: Boolean,
    isBilingual: Boolean,
    backgroundAlpha: Float,
    fontScale: Float,
    textColor: Int = -1,
    onDragStart: () -> Unit = {},
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onExpandChanged: (isExpanded: Boolean) -> Unit = {},
    onToggleLock: () -> Unit,
    onToggleBilingual: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var userInteractionToken by remember { mutableStateOf(0L) }

    // 展开态闲置 5 秒后自动收缩回极简胶囊（用户每次交互均会自动重置 5 秒倒计时）
    LaunchedEffect(isExpanded, userInteractionToken) {
        if (isExpanded) {
            delay(5000L)
            isExpanded = false
            onExpandChanged(false)
        }
    }

    val state = nowPlayingData.playbackState
    val lyrics = nowPlayingData.lyrics
    val activeIndex = nowPlayingData.currentLineIndex
    val isLoadingLyrics = nowPlayingData.isLoadingLyrics
    val currentLine = if (activeIndex in lyrics.indices) lyrics[activeIndex] else null

    // 稳定视觉骨架：即使无歌词或加载中，副行优雅回退至艺人/状态信息，彻底杜绝高度跳变 (Layout Shift)
    val (originalText, secondaryText) = remember(currentLine, state.title, state.artist, state.hasValidTrack, isBilingual, isLoadingLyrics) {
        if (currentLine != null) {
            val orig = currentLine.original.ifBlank { state.title.ifBlank { "LyricNotes 桌面歌词" } }
            val trans = if (isBilingual && !currentLine.translation.isNullOrBlank()) {
                currentLine.translation
            } else if (state.artist.isNotBlank()) {
                state.artist
            } else null
            orig to trans
        } else {
            if (state.hasValidTrack) {
                val orig = state.title.ifBlank { "正在播放" }
                val trans = when {
                    isLoadingLyrics -> "歌词同步中..."
                    state.artist.isNotBlank() -> state.artist
                    else -> "暂无滚动歌词"
                }
                orig to trans
            } else {
                "LyricNotes 桌面歌词" to "未在播放音乐"
            }
        }
    }

    val hasCover = !state.coverUrl.isNullOrBlank()

    val lyricColor = if (textColor != -1) Color(textColor) else Color.White
    val secondaryColor = if (textColor != -1) Color(textColor).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.82f)

    // 智能高对比度投影：根据前景色亮度自动选择深黑或雪白投影，无论桌面壁纸是浅色还是深色，均有极强辨识度
    val isLightColor = lyricColor.luminance() > 0.40f
    val textShadow = Shadow(
        color = if (isLightColor) Color.Black.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.85f),
        offset = Offset(0f, 1.5f),
        blurRadius = 4f
    )

    // 基础毛玻璃深色背板：锁定状态下背景更加清透，非锁定时轻量通透 (默认 0.55f)
    val effectiveBgAlpha = if (isLocked) (backgroundAlpha * 0.70f).coerceAtLeast(0.18f) else backgroundAlpha
    val capsuleBackground = Color(0xFF101114).copy(alpha = effectiveBgAlpha)

    Surface(
        color = capsuleBackground,
        shape = if (isExpanded) RoundedCornerShape(22.dp) else RoundedCornerShape(26.dp),
        border = BorderStroke(
            width = if (isLocked) 0.5.dp else 0.8.dp,
            color = if (isLightColor) Color.White.copy(alpha = if (isLocked) 0.15f else 0.25f)
                    else Color.Black.copy(alpha = if (isLocked) 0.20f else 0.35f)
        ),
        shadowElevation = if (isLocked) 2.dp else 8.dp,
        modifier = Modifier
            .widthIn(min = 260.dp, max = 460.dp)
            .animateContentSize(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            .then(
                if (!isLocked) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                userInteractionToken++
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    }
                } else Modifier
            )
    ) {
        if (!isExpanded) {
            // ================= 1. 极简灵动胶囊态 (Compact Capsule) =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(
                        if (!isLocked) {
                            Modifier.clickable {
                                isExpanded = true
                                onExpandChanged(true)
                                userInteractionToken++
                            }
                        } else Modifier
                    )
            ) {
                // 左侧微型黑胶唱片 / 封面图标
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22242B))
                ) {
                    if (hasCover) {
                        AsyncImage(
                            model = state.coverUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = lyricColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 中间：当前歌词（居中折行完整展示原文与译文，避免被截断为省略号）
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // 原文行（居中换行，最大 2 行完整展示）
                    AnimatedContent(
                        targetState = originalText,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                        label = "floatingOriginal"
                    ) { targetOrig ->
                        Text(
                            text = targetOrig,
                            color = lyricColor,
                            fontSize = (13.5f * fontScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = (18f * fontScale).sp,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }

                    // 稳定展示第二行（双语译文 / 艺人信息 / 状态提示，居中换行，最大 2 行）
                    if (!secondaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        AnimatedContent(
                            targetState = secondaryText,
                            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                            label = "floatingSecondary"
                        ) { targetSec ->
                            Text(
                                text = targetSec,
                                color = secondaryColor,
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                lineHeight = (15.5f * fontScale).sp,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow)
                            )
                        }
                    }
                }

                if (!isLocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Expand",
                        tint = lyricColor.copy(alpha = 0.50f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        } else {
            // ================= 2. 拓展播控卡片态 (Expanded Card) =================
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // 顶栏：歌曲元数据与操作图标群
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.hasValidTrack) state.title else "LyricNotes",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.hasValidTrack && state.artist.isNotBlank()) {
                            Text(
                                text = state.artist,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 双语切换按钮
                    Surface(
                        color = if (isBilingual) Color(0xFF2E3345) else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .bouncyIconClickable(onClick = {
                                userInteractionToken++
                                onToggleBilingual()
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Bilingual",
                                tint = if (isBilingual) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.60f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 穿透锁定按钮
                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .bouncyIconClickable(onClick = {
                                userInteractionToken++
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleLock()
                                isExpanded = false
                                onExpandChanged(false)
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 收起卡片按钮
                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .bouncyIconClickable(onClick = {
                                isExpanded = false
                                onExpandChanged(false)
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 关闭悬浮窗按钮
                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .bouncyIconClickable(onClick = onClose)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 中间：当前歌词高光（居中折行完整展示）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = originalText,
                        color = lyricColor,
                        fontSize = (15f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = (20f * fontScale).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = textShadow)
                    )
                    if (!secondaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = secondaryText,
                            color = secondaryColor,
                            fontSize = (12.5f * fontScale).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = (17f * fontScale).sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底栏：微型媒体播控控制器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(36.dp)
                            .bouncyIconClickable(onClick = {
                                userInteractionToken++
                                onPrevious()
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
                            .bouncyIconClickable(onClick = {
                                userInteractionToken++
                                onPlayPause()
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(36.dp)
                            .bouncyIconClickable(onClick = {
                                userInteractionToken++
                                onNext()
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
