package com.linernotes.app.presentation.nowplaying

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.util.SpotifyLauncher
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.MediaPlaybackSyncService
import com.linernotes.app.core.playback.PlaybackClockState
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.presentation.common.*
import com.linernotes.app.presentation.nowplaying.components.*
import com.linernotes.app.presentation.theme.PaperWhite
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isNotificationGranted by viewModel.notificationAccessGrantedFlow.collectAsStateWithLifecycle()
    val lyricsContent = state.lyricsContent
    val trackState = remember(state.trackMetadata, state.isPlaying) {
        state.trackMetadata.toDisplayPlaybackState(isPlaying = state.isPlaying)
    }
    val context = LocalContext.current

    val hasTrack = state.trackMetadata.hasValidTrack
    var isImmersiveMode by rememberSaveable { mutableStateOf(false) }
    var showEnlargedCover by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showEnlargedCover) {
        showEnlargedCover = false
    }
    BackHandler(enabled = isImmersiveMode && !showEnlargedCover) {
        isImmersiveMode = false
    }

    LaunchedEffect(hasTrack) {
        if (!hasTrack) isImmersiveMode = false
    }

    // 固化事件闭包引用，防止因匿名函数对象变动引起子组件无谓重组
    val onLineClicked: (BilingualLyricLine) -> Unit = remember(viewModel) {
        { line: BilingualLyricLine ->
            val pos = line.startTimeMs
            if (pos != null) {
                viewModel.seekTo(pos)
            }
        }
    }
    val onAnnotationClicked: (LyricAnnotationEntity) -> Unit = remember(viewModel) {
        { annotation: LyricAnnotationEntity -> viewModel.openGeniusAnnotation(annotation) }
    }
    val onRetryLyrics: () -> Unit = remember(viewModel) { { viewModel.reloadLyrics() } }
    val onOpenManualSearch: () -> Unit = remember(viewModel) { { viewModel.openManualSearchSheet() } }
    val onOpenSongStory: () -> Unit = remember(viewModel) { { viewModel.openSongStory() } }
    val onOpenQueue: () -> Unit = remember(viewModel) { { viewModel.openQueueSheet() } }
    val onOpenSettings: () -> Unit = remember(viewModel) { { viewModel.openSettings() } }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx().coerceAtLeast(360f) }
    val swipeThresholdPx = with(density) {
        (screenWidthPx * 0.22f).coerceIn(72.dp.toPx(), 128.dp.toPx())
    }
    val flingVelocityThresholdPx = with(density) { 520.dp.toPx() }

    // 交互式左右滑动手势位移与零闪回切歌状态机（全程仅在 graphicsLayer 内读取，0 Recompose）
    val swipeOffsetX = remember { Animatable(0f) }
    val swipeContentAlpha = remember { Animatable(1f) }
    val currentTrackKey = "${trackState.trackEpoch}|${trackState.title}|${trackState.artist}|${trackState.album}"
    var awaitingSkipDirection by remember { mutableIntStateOf(0) } // -1 = 下一首, 1 = 上一首, 0 = 无待切歌

    LaunchedEffect(currentTrackKey) {
        val dir = awaitingSkipDirection
        if (dir != 0) {
            awaitingSkipDirection = 0
            // 新曲目真正到达后才从对侧柔和进场，杜绝旧歌歌词 snapTo 闪回穿帮
            swipeOffsetX.snapTo(if (dir < 0) screenWidthPx * 0.34f else -screenWidthPx * 0.34f)
            launch {
                swipeContentAlpha.animateTo(1f, tween(durationMillis = 220, easing = LinearOutSlowInEasing))
            }
            swipeOffsetX.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 360f))
        }
    }

    // 超时兜底：若远程播放器因队尾或网络原因未切歌，750ms 后平滑回正，避免界面长期隐身
    LaunchedEffect(awaitingSkipDirection) {
        if (awaitingSkipDirection != 0) {
            delay(750L)
            if (awaitingSkipDirection != 0) {
                awaitingSkipDirection = 0
                launch { swipeContentAlpha.animateTo(1f, tween(180)) }
                swipeOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = 420f))
            }
        }
    }

    fun triggerTrackSkip(direction: Int, releaseVelocityX: Float = 0f) {
        awaitingSkipDirection = direction
        coroutineScope.launch {
            val exitTarget = if (direction < 0) -screenWidthPx * 0.88f else screenWidthPx * 0.88f
            val seededVelocity = if (direction < 0) {
                releaseVelocityX.coerceAtMost(-screenWidthPx * 1.4f)
            } else {
                releaseVelocityX.coerceAtLeast(screenWidthPx * 1.4f)
            }
            launch {
                swipeContentAlpha.animateTo(0f, tween(durationMillis = 120, easing = FastOutLinearInEasing))
            }
            swipeOffsetX.animateTo(
                targetValue = exitTarget,
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 560f),
                initialVelocity = seededVelocity
            )
            if (direction < 0) viewModel.skipToNext() else viewModel.skipToPrevious()
        }
    }

    val onPlayPause: () -> Unit = remember(viewModel) { { viewModel.togglePlayPause() } }
    val onNext: () -> Unit = remember(viewModel, screenWidthPx) {
        { triggerTrackSkip(direction = -1) }
    }
    val onPrevious: () -> Unit = remember(viewModel, screenWidthPx) {
        { triggerTrackSkip(direction = 1) }
    }
    val onToggleShuffle: () -> Unit = remember(viewModel) { { viewModel.toggleShuffle() } }
    val onCycleRepeat: () -> Unit = remember(viewModel) { { viewModel.cycleRepeatMode() } }
    val onSeekTo: (Long) -> Unit = remember(viewModel) { { posMs: Long -> viewModel.seekTo(posMs) } }
    val onCloseQueue: () -> Unit = remember(viewModel) { { viewModel.closeQueueSheet() } }
    val onSkipToQueueItem: (QueueTrackItem) -> Unit = remember(viewModel) {
        { item: QueueTrackItem -> viewModel.skipToQueueItem(item) }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isDarkUi = isDark
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkUi) Color.Black else PaperWhite)
    ) {
        LockWindowRefreshRateEffect()
        // 核心主内容区 (歌词与弥散极光全屏贯通，并在呼出底部抽屉时整体呈现 iOS 18 / Apple Music 卡片堆栈纵深后退与圆角形态演变)
        val isAnySheetOpen = state.isSettingsSheetOpen || state.isGeniusSheetOpen || state.isSongStorySheetOpen || state.isQueueSheetOpen || state.isManualSearchSheetOpen
        val contentParallaxScale by animateFloatAsState(
            targetValue = when {
                state.isQueueSheetOpen -> 0.925f
                isAnySheetOpen -> 0.94f
                else -> 1.0f
            },
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
            label = "sheetParallaxScale"
        )
        val contentParallaxAlpha by animateFloatAsState(
            targetValue = when {
                state.isQueueSheetOpen -> 0.72f
                isAnySheetOpen -> 0.84f
                else -> 1.0f
            },
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
            label = "sheetParallaxAlpha"
        )
        val contentParallaxOffsetY by animateDpAsState(
            targetValue = when {
                state.isQueueSheetOpen -> 16.dp
                isAnySheetOpen -> 12.dp
                else -> 0.dp
            },
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
            label = "sheetParallaxOffsetY"
        )
        val contentCornerRadius by animateDpAsState(
            targetValue = if (isAnySheetOpen) 28.dp else 0.dp,
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f),
            label = "sheetCornerRadius"
        )

        val trackSwipeModifier = if (hasTrack && !isAnySheetOpen && !showEnlargedCover) {
            Modifier.pointerInput(screenWidthPx, swipeThresholdPx, hasTrack) {
                val velocityTracker = VelocityTracker()
                var hasTriggeredHaptic = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        hasTriggeredHaptic = false
                        awaitingSkipDirection = 0
                        coroutineScope.launch {
                            swipeContentAlpha.snapTo(1f)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val current = swipeOffsetX.value
                        // 渐进式橡皮筋阻尼 (Rubber-Band Resistance)：超过切歌阈值后阻力加倍，赋予真实拉力张力
                        val damping = if (kotlin.math.abs(current) < swipeThresholdPx) 0.85f else 0.42f
                        val newOffset = current + dragAmount * damping
                        coroutineScope.launch {
                            swipeOffsetX.snapTo(newOffset)
                        }
                        if (!hasTriggeredHaptic && kotlin.math.abs(newOffset) >= swipeThresholdPx) {
                            hasTriggeredHaptic = true
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (e: Exception) {}
                        } else if (hasTriggeredHaptic && kotlin.math.abs(newOffset) < swipeThresholdPx) {
                            hasTriggeredHaptic = false
                        }
                    },
                    onDragEnd = {
                        val currentOffset = swipeOffsetX.value
                        val vx = velocityTracker.calculateVelocity().x
                        val flickNext = currentOffset <= -swipeThresholdPx ||
                            (currentOffset < -24f && vx <= -flingVelocityThresholdPx)
                        val flickPrev = currentOffset >= swipeThresholdPx ||
                            (currentOffset > 24f && vx >= flingVelocityThresholdPx)

                        when {
                            flickNext -> triggerTrackSkip(direction = -1, releaseVelocityX = vx)
                            flickPrev -> triggerTrackSkip(direction = 1, releaseVelocityX = vx)
                            else -> coroutineScope.launch {
                                swipeOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                                    initialVelocity = vx
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            swipeOffsetX.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 460f))
                        }
                    }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentParallaxScale
                    scaleY = contentParallaxScale
                    translationY = contentParallaxOffsetY.toPx()
                    alpha = contentParallaxAlpha
                    transformOrigin = TransformOrigin(0.5f, 0.15f)
                    if (contentCornerRadius > 0.5.dp) {
                        shape = RoundedCornerShape(contentCornerRadius)
                        clip = true
                    }
                }
                .background(
                    if (isDarkUi) {
                        if (state.isAmoledMode) Color.Black else Color(0xFF070709)
                    } else MaterialTheme.colorScheme.background
                )
        ) {
            // 1. 灵动流体弥散背景 (随主卡片一起参与 iOS 纵深缩放与圆角裁切)
            AmbientGlowBackground(
                coverUrl = trackState.coverUrl,
                isDark = isDarkUi,
                isAmoledMode = state.isAmoledMode,
                isDynamicAurora = state.isDynamicAuroraEnabled,
                modifier = Modifier.fillMaxSize()
            )

            if (hasTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(trackSwipeModifier)
                        .graphicsLayer {
                            translationX = swipeOffsetX.value
                            val dragRatio = (kotlin.math.abs(swipeOffsetX.value) / screenWidthPx).coerceIn(0f, 1f)
                            alpha = (swipeContentAlpha.value * (1f - dragRatio * 0.45f)).coerceIn(0f, 1f)
                        }
                ) {
                    NowPlayingLyricsSlot(
                        lyrics = lyricsContent.lyrics,
                        currentLineIndexFlow = viewModel.currentLineIndexFlow,
                        playbackClockFlow = viewModel.playbackClockFlow,
                        annotatedLines = lyricsContent.annotatedLines,
                        isPlaying = state.isPlaying,
                        isTraditional = state.isTraditionalChinese,
                        isLoadingLyrics = lyricsContent.isLoadingLyrics,
                        isDark = isDarkUi,
                        isAmoledMode = state.isAmoledMode,
                        baseTrackState = trackState,
                        lyricOffsetMs = state.lyricOffsetMs,
                        isWordByWordEnabled = state.isWordByWordEnabled,
                        lyricAlignment = state.lyricAlignment,
                        lyricTextSize = state.lyricTextSize,
                        showPlaybackControls = state.showPlaybackControls && !isImmersiveMode,
                        furiganaMode = state.furiganaMode,
                        onLineClicked = onLineClicked,
                        onAnnotationClicked = onAnnotationClicked,
                        onRetryLyrics = onRetryLyrics,
                        onOpenManualSearch = onOpenManualSearch
                    )
                }

                // 左右滑动指示器微徽标（仅在超过死区阈值切换可见状态，位移与透明度全走 graphicsLayer 实现 0 Recompose）
                val swipeDirection by remember {
                    derivedStateOf {
                        if (awaitingSkipDirection != 0) 0
                        else when {
                            swipeOffsetX.value < -24f -> -1
                            swipeOffsetX.value > 24f -> 1
                            else -> 0
                        }
                    }
                }
                val isThresholdArmed by remember(swipeThresholdPx) {
                    derivedStateOf { kotlin.math.abs(swipeOffsetX.value) >= swipeThresholdPx }
                }
                if (swipeDirection != 0) {
                    val armedBonusScale by animateFloatAsState(
                        targetValue = if (isThresholdArmed) 1.16f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                        label = "swipeArmedBonusScale"
                    )
                    val indicatorBg = when {
                        isThresholdArmed && isDarkUi -> Color.White.copy(alpha = 0.30f)
                        isThresholdArmed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        isDarkUi -> Color.White.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    }
                    val indicatorBorderColor = when {
                        isThresholdArmed && isDarkUi -> Color.White.copy(alpha = 0.55f)
                        isThresholdArmed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        isDarkUi -> Color.White.copy(alpha = 0.28f)
                        else -> Color.Black.copy(alpha = 0.14f)
                    }
                    val indicatorTint = when {
                        isDarkUi -> Color.White
                        isThresholdArmed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = if (swipeDirection < 0) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            color = indicatorBg,
                            shape = CircleShape,
                            border = BorderStroke(if (isThresholdArmed) 1.2.dp else 0.5.dp, indicatorBorderColor),
                            shadowElevation = if (isDarkUi) 0.dp else 6.dp,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    val ratio = (kotlin.math.abs(swipeOffsetX.value) / swipeThresholdPx).coerceIn(0f, 1f)
                                    val baseScale = (0.74f + 0.26f * ratio) * armedBonusScale
                                    scaleX = baseScale
                                    scaleY = baseScale
                                    alpha = ratio
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (swipeDirection < 0) Icons.Default.SkipNext else Icons.Default.SkipPrevious,
                                    contentDescription = null,
                                    tint = indicatorTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                NowPlayingIdleContent(
                    isDark = isDarkUi,
                    isNotificationGranted = isNotificationGranted,
                    onGrantPermission = {
                        MediaPlaybackSyncService.openNotificationAccessSettings(context)
                    },
                    onLaunchSpotify = {
                        SpotifyLauncher.launchSpotify(context)
                    }
                )
            }

            // 3. 极简顶部状态微栏 (为歌词留出 80% 屏幕纵向空间，集成外层歌曲故事直达入口与全屏沉浸入口)
            AnimatedVisibility(
                visible = !isImmersiveMode,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.88f, stiffness = 320f)) { -it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                NowPlayingTopBar(
                    trackState = trackState,
                    hasSongStory = lyricsContent.songStory != null,
                    isTraditional = state.isTraditionalChinese,
                    isDark = isDarkUi,
                    onCoverClick = { showEnlargedCover = true },
                    onOpenSongStory = onOpenSongStory,
                    onOpenQueue = onOpenQueue,
                    onOpenSettings = onOpenSettings,
                    onOpenManualSearch = onOpenManualSearch,
                    onToggleImmersive = { isImmersiveMode = !isImmersiveMode }
                )
            }

            // 全屏沉浸模式：浮动退出微胶囊 (灵动毛玻璃，轻触恢复标准视口)
            AnimatedVisibility(
                visible = isImmersiveMode && hasTrack,
                enter = fadeIn(spring(dampingRatio = 0.86f, stiffness = 320f)) + scaleIn(spring(dampingRatio = 0.86f, stiffness = 320f), initialScale = 0.85f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp)
            ) {
                Surface(
                    color = if (isDarkUi) Color(0xFF1E2230).copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.5.dp, if (isDarkUi) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .bouncyClickable(pressedScale = 0.92f) { isImmersiveMode = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = strings.exitFullscreen,
                            tint = if (isDarkUi) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.exitFullscreen,
                            color = if (isDarkUi) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // 4. Genius 获取失败/状态提醒微浮标 (iOS Dynamic Island 悬浮微胶囊风格，带果冻弹性展开)
            AnimatedVisibility(
                visible = !isImmersiveMode && (lyricsContent.geniusNoticeMessage != null || lyricsContent.isLoadingGenius) && hasTrack,
                enter = fadeIn(tween(180)) +
                        slideInVertically(spring(dampingRatio = 0.76f, stiffness = 380f)) { -it / 2 } +
                        scaleIn(spring(dampingRatio = 0.76f, stiffness = 380f), initialScale = 0.82f),
                exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 2 } + scaleOut(tween(150), targetScale = 0.86f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = if (isDarkUi) {
                        if (state.isAmoledMode) Color(0xFF0C0C0E) else Color(0xFF1E202B).copy(alpha = 0.94f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isDarkUi) {
                            if (state.isAmoledMode) Color(0xFF222226) else Color.White.copy(alpha = 0.12f)
                        } else {
                            Color.Black.copy(alpha = 0.08f)
                        }
                    ),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (lyricsContent.isLoadingGenius) "⏳" else "💡", fontSize = 12.sp)
                        val noticeMsg = if (lyricsContent.isLoadingGenius) {
                            strings.searchingGeniusStatus
                        } else {
                            val raw = lyricsContent.geniusNoticeMessage ?: ""
                            if (state.isTraditionalChinese) ChineseConverter.toTraditional(raw) else raw
                        }
                        Text(
                            text = noticeMsg,
                            color = if (isDarkUi) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        if (lyricsContent.isLoadingGenius) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                val geniusAccent = if (isDarkUi) Color(0xFF1ED760) else Color(0xFF0A8F3F)
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = geniusAccent
                                )
                                Text(
                                    text = strings.searchingStatus,
                                    color = geniusAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        } else {
                            val geniusAccent = if (isDarkUi) Color(0xFF1ED760) else Color(0xFF0A8F3F)
                            Text(
                                text = strings.retryBtn,
                                color = geniusAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .bouncyClickable(pressedScale = 0.92f) { viewModel.retryGenius() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.closeBtn,
                            tint = if (isDarkUi) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .bouncyIconClickable { viewModel.dismissGeniusNotice() }
                        )
                    }
                }
            }

            // 5. 底栏悬浮毛玻璃全能胶囊 (物理弹簧与呼吸式扩展，集合歌曲信息、滑条与播控)
            AnimatedVisibility(
                visible = hasTrack && state.showPlaybackControls && !isImmersiveMode,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.88f, stiffness = 320f)) { it / 3 } + scaleIn(initialScale = 0.95f),
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 3 } + scaleOut(targetScale = 0.95f),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NowPlayingFloatingGlassPlayer(
                    trackState = trackState,
                    playbackClockFlow = viewModel.playbackClockFlow,
                    isTraditional = state.isTraditionalChinese,
                    isDark = isDarkUi,
                    isAmoledMode = state.isAmoledMode,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onSeekTo = onSeekTo,
                    onOpenQueue = onOpenQueue,
                    onOpenSettings = onOpenSettings
                )
            }

            // 6. 高清专辑封面轻触放大浮层 (iOS / Apple Music 触感流体微弹簧缩放)
            AnimatedVisibility(
                visible = showEnlargedCover && !trackState.coverUrl.isNullOrBlank(),
                enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.78f, stiffness = 380f), initialScale = 0.80f),
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.82f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.82f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showEnlargedCover = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 28.dp,
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(trackState.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // 待播队列 (Queue) 底部抽屉 (仿 Spotify 队列界面)
        if (state.isQueueSheetOpen) {
            NowPlayingQueueSheet(
                trackState = trackState,
                isAmoledMode = state.isAmoledMode,
                sleepTimerMinutesLeft = state.sleepTimerMinutesLeft,
                onSetSleepTimer = viewModel::setSleepTimer,
                onCancelSleepTimer = viewModel::cancelSleepTimer,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onPlayPause = onPlayPause,
                onSkipToQueueItem = onSkipToQueueItem,
                onDismiss = onCloseQueue
            )
        }

        // 统一设置抽屉 (包含时间轴微调、繁简、假名、外观主题、语言、控制栏开关等)
        if (state.isSettingsSheetOpen) {
            LyricNotesSettingsSheet(
                trackState = trackState,
                hasSongStory = lyricsContent.songStory != null,
                geniusNotice = lyricsContent.geniusNoticeMessage,
                isLoadingGenius = lyricsContent.isLoadingGenius,
                furiganaMode = state.furiganaMode,
                isTraditionalChinese = state.isTraditionalChinese,
                themeMode = state.themeMode,
                appLanguage = state.appLanguage,
                isAmoledMode = state.isAmoledMode,
                lyricAlignment = state.lyricAlignment,
                lyricTextSize = state.lyricTextSize,
                isDynamicAuroraEnabled = state.isDynamicAuroraEnabled,
                isWordByWordEnabled = state.isWordByWordEnabled,
                isDeCensorEnabled = state.isDeCensorEnabled,
                showPlaybackControls = state.showPlaybackControls,
                songOffsetMs = state.songOffsetMs,
                globalOffsetMs = state.globalOffsetMs,
                lyricOffsetMs = state.lyricOffsetMs,
                onOpenSongStory = { viewModel.openSongStory() },
                onReloadGenius = { viewModel.retryGenius() },
                onAdjustSongOffset = { delta -> viewModel.adjustSongOffset(delta) },
                onResetSongOffset = { viewModel.resetSongOffset() },
                onAdjustGlobalOffset = { delta -> viewModel.adjustGlobalOffset(delta) },
                onResetGlobalOffset = { viewModel.resetGlobalOffset() },
                onAdjustOffset = { delta -> viewModel.adjustLyricOffset(delta) },
                onResetOffset = { viewModel.resetLyricOffset() },
                onSetFuriganaMode = { mode -> viewModel.setFuriganaMode(mode) },
                onToggleTraditionalChinese = { viewModel.toggleTraditionalChinese() },
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onSetAppLanguage = { viewModel.setAppLanguage(it) },
                onSetAmoledMode = { viewModel.setAmoledMode(it) },
                onSetLyricAlignment = { viewModel.setLyricAlignment(it) },
                onSetLyricTextSize = { viewModel.setLyricTextSize(it) },
                onSetDynamicAuroraEnabled = { viewModel.setDynamicAuroraEnabled(it) },
                onSetWordByWordEnabled = { viewModel.setWordByWordEnabled(it) },
                onToggleDeCensor = { viewModel.toggleDeCensor() },
                onTogglePlaybackControls = { viewModel.togglePlaybackControls() },
                onReloadLyrics = { viewModel.reloadLyrics() },
                onOpenManualSearch = onOpenManualSearch,
                onDismiss = { viewModel.closeSettings() }
            )
        }

        // 手动搜索与更换歌词版本抽屉
        if (state.isManualSearchSheetOpen) {
            ManualLyricSearchSheet(
                trackTitle = trackState.title,
                artistName = trackState.artist,
                targetDurationMs = trackState.durationMs,
                candidates = state.manualCandidates,
                isSearching = state.isSearchingCandidates,
                isApplying = state.isApplyingCandidate,
                hasManualBinding = state.hasManualBinding,
                errorMessage = state.candidateSearchError,
                isTraditional = state.isTraditionalChinese,
                isAmoledMode = state.isAmoledMode,
                onSearch = { query -> viewModel.searchCandidates(query) },
                onSelectCandidate = { candidate -> viewModel.selectCandidate(candidate) },
                onResetToAutoMatch = { viewModel.resetToAutoMatch() },
                onDismiss = { viewModel.closeManualSearchSheet() }
            )
        }

        // Genius Behind The Lyrics 底部抽屉
        if (state.isGeniusSheetOpen) {
            val matchedLyricTranslation = remember(state.selectedAnnotation, lyricsContent.lyrics) {
                val annot = state.selectedAnnotation ?: return@remember null
                if (!annot.lyricTranslation.isNullOrBlank()) {
                    return@remember annot.lyricTranslation
                }
                val frag = annot.lyricFragment.trim().lowercase()
                if (frag.isNotBlank()) {
                    val matched = lyricsContent.lyrics.filter { line ->
                        val orig = line.original.trim().lowercase()
                        orig.isNotBlank() && (frag.contains(orig) || orig.contains(frag)) && line.translation.isNotBlank()
                    }
                    if (matched.isNotEmpty()) {
                        matched.joinToString("\n") { it.translation }
                    } else null
                } else null
            }

            NowPlayingGeniusSheet(
                annotation = state.selectedAnnotation,
                lyricTranslation = matchedLyricTranslation,
                isTranslating = state.isAnnotationTranslating,
                isTraditional = state.isTraditionalChinese,
                isAmoledMode = state.isAmoledMode,
                onRetryTranslation = { viewModel.retryAnnotationTranslation() },
                onDismiss = { viewModel.closeGeniusSheet() }
            )
        }

        // 歌曲背景故事抽屉
        if (state.isSongStorySheetOpen) {
            NowPlayingSongStorySheet(
                story = lyricsContent.songStory,
                isTranslating = state.isSongStoryTranslating,
                isTraditional = state.isTraditionalChinese,
                isAmoledMode = state.isAmoledMode,
                onRetryTranslation = { viewModel.retrySongStoryTranslation() },
                onDismiss = { viewModel.closeSongStory() }
            )
        }
    }
}

@Composable
private fun NowPlayingLyricsSlot(
    lyrics: List<BilingualLyricLine>,
    currentLineIndexFlow: StateFlow<Int>,
    playbackClockFlow: StateFlow<PlaybackClockState>,
    annotatedLines: Map<Int, LyricAnnotationEntity>,
    baseTrackState: TrackPlaybackState,
    isPlaying: Boolean,
    isTraditional: Boolean,
    isLoadingLyrics: Boolean,
    isDark: Boolean,
    isAmoledMode: Boolean,
    lyricOffsetMs: Long,
    isWordByWordEnabled: Boolean,
    lyricAlignment: Int,
    lyricTextSize: Int,
    showPlaybackControls: Boolean,
    furiganaMode: FuriganaDisplayMode,
    onLineClicked: (BilingualLyricLine) -> Unit,
    onAnnotationClicked: (LyricAnnotationEntity) -> Unit,
    onRetryLyrics: () -> Unit,
    onOpenManualSearch: () -> Unit
) {
    val currentLineIndex by currentLineIndexFlow.collectAsStateWithLifecycle()
    val playbackClock by playbackClockFlow.collectAsStateWithLifecycle()
    val effectiveTrackState = remember(baseTrackState, playbackClock) {
        baseTrackState.withClock(playbackClock)
    }
    NowPlayingLyricsContent(
        lyrics = lyrics,
        currentLineIndex = currentLineIndex,
        annotatedLines = annotatedLines,
        isPlaying = playbackClock.isPlaying,
        isTraditional = isTraditional,
        isLoadingLyrics = isLoadingLyrics,
        isDark = isDark,
        isAmoledMode = isAmoledMode,
        trackState = effectiveTrackState,
        lyricOffsetMs = lyricOffsetMs,
        isWordByWordEnabled = isWordByWordEnabled,
        lyricAlignment = lyricAlignment,
        lyricTextSize = lyricTextSize,
        showPlaybackControls = showPlaybackControls,
        furiganaMode = furiganaMode,
        onLineClicked = onLineClicked,
        onAnnotationClicked = onAnnotationClicked,
        onRetryLyrics = onRetryLyrics,
        onOpenManualSearch = onOpenManualSearch
    )
}
