package com.linernotes.app.presentation.nowplaying.components

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.PlaybackClockState
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.presentation.common.bouncyIconClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 现代旗舰级无界流体播控栏 (Apple Music 原生「去容器化」规范)
 * 1. 材质：彻底移除厚重深色卡片胶囊，图标与进度线完全以无容器（Containerless）纯粹晶体悬浮于动态极光之上
 * 2. 进度：全宽触控声学极细流线与 Mono 时间指示 (已播时间 / 倒计时剩余时间)
 * 3. 布局：对称五联键 —— [Shuffle 40dp] <--> [Prev 44dp] <--> [52dp Hero Play] <--> [Next 44dp] <--> [Repeat 40dp]
 */
@Composable
fun NowPlayingFloatingGlassPlayer(
    trackState: TrackPlaybackState,
    playbackClockFlow: StateFlow<PlaybackClockState>,
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
    val strings = LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        // 1. 顶部声学进度轨 (隔离收集高频时钟流，进度心跳与 Seek 仅局部重组本进度条)
        PlayerProgressSlot(
            baseTrackState = trackState,
            playbackClockFlow = playbackClockFlow,
            isDark = isDark,
            onPlayerTextSec = onPlayerTextSec,
            onSeekTo = onSeekTo,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 2. 现代无界流体播控群 (Apple Music 纯净 5 联键：[随机] [上一首] [52dp 白瓷播放] [下一首] [循环])
        val coroutineScope = rememberCoroutineScope()
        val prevNudgeX = remember { Animatable(0f) }
        val nextNudgeX = remember { Animatable(0f) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 随机播放 (Shuffle) - 激活态流体光晕脉冲
            val isShuffle = trackState.isShuffleActive
            val shuffleBg by animateColorAsState(
                targetValue = if (isShuffle) (if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Color.Transparent,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 500f),
                label = "shuffleBg"
            )
            val shuffleTint by animateColorAsState(
                targetValue = if (isShuffle) (if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary) else iconTint.copy(alpha = 0.55f),
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 500f),
                label = "shuffleTint"
            )
            val shuffleScale by animateFloatAsState(
                targetValue = if (isShuffle) 1.06f else 1.0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 480f),
                label = "shuffleScale"
            )
            Surface(
                color = shuffleBg,
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = shuffleScale
                        scaleY = shuffleScale
                    }
                    .bouncyIconClickable(onClick = onToggleShuffle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (isShuffle) strings.shuffleOnDesc else strings.shuffleOffDesc,
                        tint = shuffleTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. 上一首 (Apple Music 定向动量左推回弹)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        translationX = prevNudgeX.value
                    }
                    .clip(CircleShape)
                    .bouncyIconClickable(
                        onClick = {
                            coroutineScope.launch {
                                prevNudgeX.snapTo(-14f)
                                prevNudgeX.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 460f))
                            }
                            onPrevious()
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = strings.previousTrackDesc,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 3. 核心中央 Hero 播放/暂停白瓷圆钮 (52dp 旗舰流体微弹簧 + 15° 旋转缩放交融)
            val heroScale by animateFloatAsState(
                targetValue = if (trackState.isPlaying) 1.04f else 1.0f,
                animationSpec = spring(dampingRatio = 0.76f, stiffness = 460f),
                label = "heroScale"
            )
            val heroElevation by animateDpAsState(
                targetValue = if (trackState.isPlaying) 12.dp else 4.dp,
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
                    val iconRotation by animateFloatAsState(
                        targetValue = if (trackState.isPlaying) 0f else -12f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
                        label = "heroIconRotation"
                    )
                    AnimatedContent(
                        targetState = trackState.isPlaying,
                        transitionSpec = {
                            (fadeIn(tween(130, easing = LinearOutSlowInEasing)) +
                                scaleIn(initialScale = 0.65f, animationSpec = spring(dampingRatio = 0.74f, stiffness = 580f))) togetherWith
                                (fadeOut(tween(100)) + scaleOut(targetScale = 0.65f))
                        },
                        label = "playPauseMorph"
                    ) { isPlaying ->
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) strings.pauseDesc else strings.playDesc,
                            tint = if (isDark) Color(0xFF0F1118) else Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    rotationZ = if (isPlaying) 0f else iconRotation
                                }
                        )
                    }
                }
            }

            // 4. 下一首 (Apple Music 定向动量右推回弹)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        translationX = nextNudgeX.value
                    }
                    .clip(CircleShape)
                    .bouncyIconClickable(
                        onClick = {
                            coroutineScope.launch {
                                nextNudgeX.snapTo(14f)
                                nextNudgeX.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 460f))
                            }
                            onNext()
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = strings.nextTrackDesc,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 5. 循环模式 (Repeat) - 状态形态平滑切换与激活脉冲
            val isRepeatActive = trackState.repeatMode != 0
            val repeatBg by animateColorAsState(
                targetValue = if (isRepeatActive) (if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Color.Transparent,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 500f),
                label = "repeatBg"
            )
            val repeatTint by animateColorAsState(
                targetValue = if (isRepeatActive) (if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary) else iconTint.copy(alpha = 0.55f),
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 500f),
                label = "repeatTint"
            )
            val repeatScale by animateFloatAsState(
                targetValue = if (isRepeatActive) 1.06f else 1.0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 480f),
                label = "repeatScale"
            )
            Surface(
                color = repeatBg,
                shape = CircleShape,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = repeatScale
                        scaleY = repeatScale
                    }
                    .bouncyIconClickable(onClick = onCycleRepeat)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = trackState.repeatMode,
                        transitionSpec = {
                            (fadeIn(tween(120)) + scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 550f))) togetherWith
                                (fadeOut(tween(90)) + scaleOut(targetScale = 0.72f))
                        },
                        label = "repeatModeMorph"
                    ) { mode ->
                        Icon(
                            imageVector = if (mode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = when (mode) {
                                1 -> strings.repeatAllDesc
                                2 -> strings.repeatOneDesc
                                else -> strings.repeatOffDesc
                            },
                            tint = repeatTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerProgressSlot(
    baseTrackState: TrackPlaybackState,
    playbackClockFlow: StateFlow<PlaybackClockState>,
    isDark: Boolean,
    onPlayerTextSec: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val clock by playbackClockFlow.collectAsStateWithLifecycle()
    val progressTrackState = remember(baseTrackState, clock) {
        baseTrackState.withClock(clock)
    }
    PlayerProgressSection(
        trackState = progressTrackState,
        isDark = isDark,
        onPlayerTextSec = onPlayerTextSec,
        onSeekTo = onSeekTo,
        modifier = modifier
    )
}

@Composable
fun PlayerProgressSection(
    trackState: TrackPlaybackState,
    isDark: Boolean,
    onPlayerTextSec: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidDuration = trackState.durationMs > 0L
    val duration = if (hasValidDuration) trackState.durationMs else 1L
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // 乐观进度锁定：松手提交 Seek 后短暂锁定目标时刻，等待远端播放器广播追上或超时 (850ms)，彻底杜绝滑块闪跳弹回
    var seekLockTargetMs by remember(trackState.trackEpoch) { mutableStateOf<Long?>(null) }
    var seekLockDeadlineMs by remember(trackState.trackEpoch) { mutableLongStateOf(0L) }

    fun resolveCurrentPositionMs(): Long {
        if (isScrubbing && hasValidDuration) {
            return (scrubFraction * duration).toLong().coerceIn(0L, duration)
        }
        val lockTarget = seekLockTargetMs
        val systemEst = trackState.getEstimatedPositionMs()
        if (lockTarget != null) {
            val withinTolerance = kotlin.math.abs(systemEst - lockTarget) <= 1800L
            if (!withinTolerance && SystemClock.elapsedRealtime() < seekLockDeadlineMs) {
                return lockTarget.coerceIn(0L, duration)
            }
        }
        return systemEst
    }

    var displayedSecondMs by remember(trackState.trackEpoch) {
        mutableLongStateOf((resolveCurrentPositionMs() / 1000L) * 1000L)
    }

    // 文本时间戳仅在播放态且跨过整秒边界时更新 (~1Hz)，暂停态 0 次唤醒
    LaunchedEffect(trackState, hasValidDuration, isScrubbing, seekLockTargetMs) {
        val currentMs = resolveCurrentPositionMs()
        displayedSecondMs = (currentMs / 1000L) * 1000L
        if (!trackState.isPlaying || !hasValidDuration || isScrubbing) {
            if (seekLockTargetMs != null && kotlin.math.abs(trackState.getEstimatedPositionMs() - seekLockTargetMs!!) <= 1800L) {
                seekLockTargetMs = null
            }
            return@LaunchedEffect
        }
        while (isActive) {
            val est = resolveCurrentPositionMs()
            if (seekLockTargetMs != null &&
                (SystemClock.elapsedRealtime() >= seekLockDeadlineMs || kotlin.math.abs(trackState.getEstimatedPositionMs() - seekLockTargetMs!!) <= 1800L)
            ) {
                seekLockTargetMs = null
            }
            displayedSecondMs = (est / 1000L) * 1000L
            val delayToNextSec = 1000L - (est % 1000L)
            delay(delayToNextSec.coerceIn(100L, 1000L))
        }
    }

    val textPositionMs = if (isScrubbing && hasValidDuration) {
        (scrubFraction * duration).toLong().coerceIn(0L, duration)
    } else {
        displayedSecondMs
    }
    val remainingMs = if (hasValidDuration) {
        (duration - textPositionMs).coerceAtLeast(0L)
    } else {
        0L
    }

    val timeScale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 450f),
        label = "scrubTimeScale"
    )
    val activeTimeColor = if (isScrubbing) {
        if (isDark) Color.White else Color.Black
    } else {
        onPlayerTextSec
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatProgressMs(textPositionMs),
            color = activeTimeColor,
            fontSize = 11.sp,
            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .widthIn(min = 38.dp)
                .graphicsLayer {
                    scaleX = timeScale
                    scaleY = timeScale
                },
            textAlign = TextAlign.Start
        )

        SleekTrackScrubber(
            hasValidDuration = hasValidDuration,
            durationMs = duration,
            isPlaying = trackState.isPlaying,
            progressProvider = {
                if (hasValidDuration) (resolveCurrentPositionMs().toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            },
            isDark = isDark,
            onScrubStateChange = { dragging, frac ->
                isScrubbing = dragging
                scrubFraction = frac
            },
            onSeekToFraction = { frac ->
                if (hasValidDuration) {
                    val targetMs = (frac * duration).toLong().coerceIn(0L, duration)
                    seekLockTargetMs = targetMs
                    seekLockDeadlineMs = SystemClock.elapsedRealtime() + 850L
                    displayedSecondMs = (targetMs / 1000L) * 1000L
                    onSeekTo(targetMs)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Text(
            text = if (hasValidDuration) "-${formatProgressMs(remainingMs)}" else "--:--",
            color = if (isScrubbing) activeTimeColor else onPlayerTextSec.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .widthIn(min = 44.dp)
                .graphicsLayer {
                    scaleX = timeScale
                    scaleY = timeScale
                },
            textAlign = TextAlign.End
        )
    }
}

private fun formatProgressMs(ms: Long): String {
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

/**
 * 极简平滑声学进度轨 (48dp 无障碍热区 + 统一单手势无死区跟手 + 指尖悬浮时间预演气泡 + Draw/GPU 阶段 120Hz 零重组推演)
 */
@Composable
fun SleekTrackScrubber(
    hasValidDuration: Boolean,
    durationMs: Long,
    isPlaying: Boolean,
    progressProvider: () -> Float,
    isDark: Boolean,
    onScrubStateChange: (Boolean, Float) -> Unit = { _, _ -> },
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // 仅在播放态且非手动拖拽时驱动 Draw 帧时钟；进度条每秒推进 < 10dp，~16Hz 刷新即可保证亚像素丝滑并大幅降低 GPU/RenderThread 无谓重绘
    val frameClock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, hasValidDuration, isDragging) {
        if (isPlaying && hasValidDuration && !isDragging) {
            while (isActive) {
                frameClock.longValue = SystemClock.elapsedRealtime()
                delay(64L)
            }
        }
    }

    fun resolveFraction(): Float {
        return if (isDragging) dragFraction.coerceIn(0f, 1f) else progressProvider().coerceIn(0f, 1f)
    }

    // 触感物理绽放动力学：手指按压/拖拽时膨胀至 6dp，松手时平滑沉降回 3dp 极细流线
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "scrubberTrackHeight"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "scrubberThumbSize"
    )
    val haloScale by animateFloatAsState(
        targetValue = if (isDragging) 1.0f else 0.0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "scrubberHaloScale"
    )

    val trackBaseColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.14f)
    val trackActiveColor = if (isDark) Color.White else Color(0xFF19191C)
    val thumbColor = if (isDark) Color.White else Color(0xFF19191C)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(hasValidDuration) {
                if (!hasValidDuration) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var frac = (down.position.x / width).coerceIn(0f, 1f)
                    isDragging = true
                    dragFraction = frac
                    onScrubStateChange(true, frac)
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {}

                    var committed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            break
                        }
                        if (!change.pressed) {
                            committed = change.changedToUpIgnoreConsumed()
                            change.consume()
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            frac = (change.position.x / width).coerceIn(0f, 1f)
                            dragFraction = frac
                            onScrubStateChange(true, frac)
                        }
                    }

                    isDragging = false
                    onScrubStateChange(false, frac)
                    if (committed) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (_: Exception) {}
                        onSeekToFraction(frac)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current

        // 槽轨底色与高光槽轨 (在 Draw 阶段读取 frameClock 与进度，0 Measure/Layout/Recompose 开销)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            frameClock.longValue
            val frac = resolveFraction()
            val h = size.height
            val radius = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(
                color = trackBaseColor,
                size = size,
                cornerRadius = radius
            )
            val activeWidth = size.width * frac
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = trackActiveColor,
                    size = Size(activeWidth, h),
                    cornerRadius = radius
                )
            }
        }

        // 指尖上方悬浮实时时间预览气泡 (仅拖拽交互时显示)
        if (haloScale > 0.02f && hasValidDuration) {
            var bubbleWidthPx by remember { mutableIntStateOf(0) }
            val previewMs = (dragFraction * durationMs).toLong().coerceIn(0L, durationMs)
            val liftPx = with(density) { 36.dp.toPx() }
            Surface(
                color = if (isDark) Color(0xF01C1E28) else Color(0xF519191C),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 6.dp,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.22f else 0.14f)),
                modifier = Modifier
                    .onSizeChanged { bubbleWidthPx = it.width }
                    .graphicsLayer {
                        val halfW = (bubbleWidthPx / 2f).coerceAtLeast(with(density) { 24.dp.toPx() })
                        val centerX = widthPx * dragFraction.coerceIn(0f, 1f)
                        translationX = (centerX - halfW).coerceIn(0f, (widthPx - bubbleWidthPx).coerceAtLeast(0f))
                        translationY = -liftPx
                        val s = 0.86f + 0.14f * haloScale
                        scaleX = s
                        scaleY = s
                        alpha = haloScale
                    }
            ) {
                Text(
                    text = formatProgressMs(previewMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                )
            }
        }

        // 拖拽时的外发光呼吸声学光晕 (Halo)
        if (haloScale > 0.01f) {
            val haloSize = 24.dp
            val haloRadiusPx = with(density) { (haloSize / 2).toPx() }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        frameClock.longValue
                        val frac = resolveFraction()
                        val haloOffsetPx = ((widthPx * frac) - haloRadiusPx)
                            .coerceIn(-haloRadiusPx, (widthPx - haloRadiusPx).coerceAtLeast(0f))
                        translationX = haloOffsetPx
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloScale * 0.28f
                    }
                    .size(haloSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }

        // 白瓷晶体滑块指示点 (通过 GPU 硬件变换矩阵 translationX 位移，跳过布局重排)
        val thumbRadiusPx = with(density) { (thumbSize / 2).toPx() }
        Box(
            modifier = Modifier
                .graphicsLayer {
                    frameClock.longValue
                    val frac = resolveFraction()
                    val thumbOffsetPx = ((widthPx * frac) - thumbRadiusPx)
                        .coerceIn(0f, (widthPx - thumbRadiusPx * 2).coerceAtLeast(0f))
                    translationX = thumbOffsetPx
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.20f)
                    ),
                    CircleShape
                )
        )
    }
}
