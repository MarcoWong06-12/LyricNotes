package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.presentation.common.bouncyIconClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 现代旗舰级无界流体播控栏 (Apple Music 原生「去容器化」规范)
 * 1. 材质：彻底移除厚重深色卡片胶囊，图标与进度线完全以无容器（Containerless）纯粹晶体悬浮于动态极光之上
 * 2. 进度：全宽触控声学极细流线与 Mono 时间指示 (已播时间 / 倒计时剩余时间)
 * 3. 布局：对称五联键 —— [Shuffle 40dp] <--> [Prev 44dp] <--> [52dp Hero Play] <--> [Next 44dp] <--> [Repeat 40dp]
 */
@Composable
fun NowPlayingFloatingGlassPlayer(
    trackState: TrackPlaybackState,
    isTraditional: Boolean,
    isDark: Boolean,
    isAmoledMode: Boolean = false,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val onPlayerTextSec = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f)
    val iconTint = if (isDark) Color.White.copy(alpha = 0.94f) else Color(0xFF1E202B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        // 1. 顶部声学进度轨 (全宽极简流线，透明融入背景，左已播放时间 / 右倒计时剩余时间)
        PlayerProgressSection(
            trackState = trackState,
            isDark = isDark,
            onPlayerTextSec = onPlayerTextSec,
            onSeekTo = onSeekTo,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // 2. 现代无界流体播控群 (Apple Music 纯净 5 联键：[随机] [上一首] [52dp 白瓷播放] [下一首] [循环])
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 随机播放 (Shuffle)
            val isShuffle = trackState.isShuffleActive
            val shuffleBg by animateColorAsState(
                targetValue = if (isShuffle) (if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Color.Transparent,
                animationSpec = spring(stiffness = 600f),
                label = "shuffleBg"
            )
            val shuffleTint by animateColorAsState(
                targetValue = if (isShuffle) (if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary) else iconTint.copy(alpha = 0.55f),
                animationSpec = spring(stiffness = 600f),
                label = "shuffleTint"
            )
            Surface(
                color = shuffleBg,
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp)
                    .bouncyIconClickable(onClick = onToggleShuffle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (isShuffle) "随机播放 (已开启)" else "顺序播放",
                        tint = shuffleTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. 上一首
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .bouncyIconClickable(onClick = onPrevious)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 3. 核心中央 Hero 播放/暂停白瓷圆钮 (52dp 旗舰流体微弹簧)
            val heroScale by animateFloatAsState(
                targetValue = if (trackState.isPlaying) 1.04f else 1.0f,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 500f),
                label = "heroScale"
            )
            val heroElevation by animateDpAsState(
                targetValue = if (trackState.isPlaying) 10.dp else 4.dp,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 400f),
                label = "heroElevation"
            )
            Surface(
                color = if (isDark) Color.White else Color(0xFF19191C),
                shape = CircleShape,
                shadowElevation = heroElevation,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                    }
                    .bouncyIconClickable(
                        pressedScale = 0.88f,
                        onClick = onPlayPause
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = trackState.isPlaying,
                        transitionSpec = {
                            fadeIn(tween(130, easing = LinearOutSlowInEasing)) + scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 600f)) togetherWith
                                    fadeOut(tween(110)) + scaleOut(targetScale = 0.72f)
                        },
                        label = "playPauseMorph"
                    ) { isPlaying ->
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = if (isDark) Color(0xFF0F1118) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // 4. 下一首
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .bouncyIconClickable(onClick = onNext)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 5. 循环模式 (Repeat)
            val isRepeatActive = trackState.repeatMode != 0
            val repeatBg by animateColorAsState(
                targetValue = if (isRepeatActive) (if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Color.Transparent,
                animationSpec = spring(stiffness = 600f),
                label = "repeatBg"
            )
            val repeatTint by animateColorAsState(
                targetValue = if (isRepeatActive) (if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary) else iconTint.copy(alpha = 0.55f),
                animationSpec = spring(stiffness = 600f),
                label = "repeatTint"
            )
            Surface(
                color = repeatBg,
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp)
                    .bouncyIconClickable(onClick = onCycleRepeat)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (trackState.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = when (trackState.repeatMode) {
                            1 -> "列表循环"
                            2 -> "单曲循环"
                            else -> "顺序播放"
                        },
                        tint = repeatTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerProgressSection(
    trackState: TrackPlaybackState,
    isDark: Boolean,
    onPlayerTextSec: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var estimatedPosition by remember { mutableLongStateOf(trackState.currentPositionMs) }

    LaunchedEffect(trackState) {
        while (isActive) {
            estimatedPosition = trackState.getEstimatedPositionMs()
            delay(100)
        }
    }

    val hasValidDuration = trackState.durationMs > 0L
    val duration = if (hasValidDuration) trackState.durationMs else 1L
    val progress = if (hasValidDuration) {
        (estimatedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMs = if (hasValidDuration) {
        (duration - estimatedPosition).coerceAtLeast(0L)
    } else {
        0L
    }

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatMs(estimatedPosition),
            color = onPlayerTextSec,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(min = 38.dp),
            textAlign = TextAlign.Start
        )

        SleekTrackScrubber(
            progress = progress,
            isDark = isDark,
            onSeekToFraction = { frac ->
                if (hasValidDuration) {
                    val targetMs = (frac * duration).toLong()
                    onSeekTo(targetMs)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Text(
            text = if (hasValidDuration) "-${formatMs(remainingMs)}" else "--:--",
            color = onPlayerTextSec.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 极简平滑声学进度轨 (Apple Music / Spotify 3dp 极细流线，附带手指触碰即刻微绽放的触感动力学)
 */
@Composable
fun SleekTrackScrubber(
    progress: Float,
    isDark: Boolean,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = (if (isDragging) dragFraction else progress).coerceIn(0f, 1f)

    // 触感物理绽放动力学：手指拖拽时微膨胀，松手时平滑沉降回极细流线
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 4.5.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "scrubberTrackHeight"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 13.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 480f),
        label = "scrubberThumbSize"
    )

    val trackBaseColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.14f)
    val trackActiveColor = if (isDark) Color.White else Color(0xFF19191C)
    val thumbColor = if (isDark) Color.White else Color(0xFF19191C)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (e: Exception) {}
                    onSeekToFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (e: Exception) {}
                    },
                    onDragEnd = {
                        isDragging = false
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (e: Exception) {}
                        onSeekToFraction(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragFraction = frac
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        // 槽轨底色与高光槽轨 (在 Draw 阶段直接完成向量绘制，彻底消除每 100ms 强制触发的 Measure/Layout 排版开销，且杜绝任何拉伸形变)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val h = size.height
            val radius = CornerRadius(h / 2f, h / 2f)
            // 槽轨底色
            drawRoundRect(
                color = trackBaseColor,
                size = size,
                cornerRadius = radius
            )
            // 已播放高光槽轨
            val activeWidth = size.width * currentFraction
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = trackActiveColor,
                    size = Size(activeWidth, h),
                    cornerRadius = radius
                )
            }
        }

        // 极细圆形滑块指示点 (通过 GPU 硬件变换矩阵 translationX 位移，跳过布局重排)
        val thumbRadiusPx = with(LocalDensity.current) { (thumbSize / 2).toPx() }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val thumbOffsetPx = ((widthPx * currentFraction) - thumbRadiusPx)
                        .coerceIn(0f, (widthPx - thumbRadiusPx * 2).coerceAtLeast(0f))
                    translationX = thumbOffsetPx
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.20f)
                    ),
                    CircleShape
                )
        )
    }
}
