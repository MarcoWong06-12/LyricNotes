package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.lyric.FuriganaEngine
import com.linernotes.app.core.lyric.LyricWordParser
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricWord
import com.linernotes.app.presentation.common.*
import com.linernotes.app.presentation.nowplaying.FuriganaDisplayMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Apple Music 官方原生逆向高精度流体歌词视口
 * 1. 视口滚动流体曲线：r1 = PathInterpolator(0.75f, 0.0f, 0.25f, 1.0f)，时长根据行间隙动态映射 [480ms ~ 750ms]
 * 2. 状态缩放与透明度曲线：C.j = PathInterpolator(0.39f, 0.575f, 0.565f, 1.0f)
 * 3. 苹果官方视觉层级：活跃行 34sp Bold (1.0x 缩放, 0.94 Alpha)，非活跃行 (0.98x 缩放, 0.45/0.18 Alpha)
 * 4. 视口边缘双向羽化渐变：顶部 7% 高度遮罩，底部 27.75% + 播放控制坞高度动态渐变
 */
@Composable
fun NowPlayingLyricsContent(
    lyrics: List<BilingualLyricLine>,
    currentLineIndex: Int,
    annotatedLines: Map<Int, LyricAnnotationEntity>,
    isPlaying: Boolean,
    isTraditional: Boolean,
    isLoadingLyrics: Boolean,
    isDark: Boolean,
    isAmoledMode: Boolean = false,
    trackState: TrackPlaybackState,
    lyricOffsetMs: Long = 0L,
    isWordByWordEnabled: Boolean = true,
    lyricAlignment: Int = AiPreferences.LYRIC_ALIGN_LEFT,
    showPlaybackControls: Boolean = true,
    furiganaMode: FuriganaDisplayMode = FuriganaDisplayMode.OFF,
    onLineClicked: (BilingualLyricLine) -> Unit,
    onAnnotationClicked: (LyricAnnotationEntity) -> Unit,
    onRetryLyrics: () -> Unit = {},
    onOpenManualSearch: () -> Unit = {}
) {
    val strings = LocalStrings.current

    if (isLoadingLyrics && lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = if (isDark) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = strings.syncingLyricsStatus,
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = strings.noTimedLyricsFound,
                    color = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)),
                        modifier = Modifier
                            .bouncyClickable(pressedScale = 0.94f) { onRetryLyrics() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.retryBtn,
                                tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.retryBtn,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Surface(
                        color = if (isDark) Color(0xFF1E2235) else Color(0xFFE8EBF5),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF333852) else Color(0xFFD0D5E5)),
                        modifier = Modifier
                            .bouncyClickable(pressedScale = 0.94f) { onOpenManualSearch() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = strings.manualSearchLyricsBtn,
                                tint = if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.manualSearchLyricsBtn,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 120Hz/90Hz/60Hz VSYNC 高精度硬件级播放进度采样器 (仅在 draw 阶段读取，重构率为 0，驱动活跃行逐字光效扫掠)
    val currentPositionState = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, trackState, lyricOffsetMs, isWordByWordEnabled) {
        if (isPlaying && isWordByWordEnabled) {
            while (isActive) {
                withFrameMillis {
                    currentPositionState.longValue = (trackState.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                }
            }
        } else {
            currentPositionState.longValue = (trackState.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
        }
    }
    val currentPositionMsProvider: () -> Long = remember { { currentPositionState.longValue } }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current

    // Apple Music 官方视口黄金视线高度：活跃行几何中心严格锚定在视口偏上部约 36% 黄金阅读带
    val focalCenterDp = (screenHeightDp * 0.36f).coerceIn(240.dp, 360.dp)
    val focalCenterPx = with(density) { focalCenterDp.toPx() }
    val topFocalPadding = screenHeightDp * 0.28f
    val bottomFocalPadding = screenHeightDp * 0.38f + (if (showPlaybackControls) 130.dp else 48.dp)

    // Apple Music 官方逆向流体曲线与动态滚动时长 (PathInterpolator 0.75, 0.0, 0.25, 1.0)
    val scrollDurationMs = remember(currentLineIndex, lyrics) {
        val currentLine = lyrics.getOrNull(currentLineIndex)
        val nextLine = lyrics.getOrNull(currentLineIndex + 1)
        val curStart = currentLine?.startTimeMs
        val nxtStart = nextLine?.startTimeMs
        if (currentLine != null && nextLine != null && curStart != null && nxtStart != null) {
            val currentEnd = currentLine.endTimeMs ?: (curStart + 2000L)
            val gap = (nxtStart - currentEnd).toFloat()
            val clampedGap = gap.coerceIn(200f, 750f)
            val t = (clampedGap - 200f) / 550f
            (480f + (750f - 480f) * t).roundToInt()
        } else {
            750
        }
    }
    val lyricFluidScrollSpec = remember(scrollDurationMs) {
        tween<Float>(
            durationMillis = scrollDurationMs,
            easing = CubicBezierEasing(0.75f, 0.0f, 0.25f, 1.0f)
        )
    }

    // 监听用户真实手指触摸拖拽交互
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledAway by remember { mutableStateOf(false) }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            userScrolledAway = true
        }
    }

    LaunchedEffect(trackState.title, trackState.artist) {
        userScrolledAway = false
    }

    // 苹果原生 2.5 秒无交互自动平滑弹簧回正
    LaunchedEffect(userScrolledAway, isUserDragging) {
        if (userScrolledAway && !isUserDragging) {
            delay(2500L)
            userScrolledAway = false
        }
    }

    // 动态计算当前落在黄金视觉带最中央的歌词行索引 (基于条目几何中心，消除双语高度差异)
    val visibleFocalIndex by remember(currentLineIndex, focalCenterPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf (if (currentLineIndex in lyrics.indices) currentLineIndex else 0)
            val closest = visibleItems.minByOrNull { item ->
                val itemCenter = item.offset.toFloat() + item.size.toFloat() / 2f
                kotlin.math.abs(itemCenter - focalCenterPx)
            }
            closest?.index ?: (if (currentLineIndex in lyrics.indices) currentLineIndex else 0)
        }
    }

    LaunchedEffect(visibleFocalIndex, currentLineIndex, isUserDragging) {
        if (!isUserDragging && userScrolledAway && currentLineIndex >= 0 && visibleFocalIndex == currentLineIndex) {
            userScrolledAway = false
        }
    }

    val isBrowsing = isUserDragging || userScrolledAway

    val focalIndex = if (isBrowsing) {
        visibleFocalIndex
    } else {
        if (currentLineIndex in lyrics.indices) currentLineIndex else 0
    }

    var lastSongTitle by remember { mutableStateOf("") }

    // Apple Music 官方视线重心动力学 (36% 视口几何中心锚定 + CubicBezier 0.75, 0.0, 0.25, 1.0)
    LaunchedEffect(currentLineIndex, userScrolledAway, lyrics, trackState.title) {
        if (userScrolledAway || lyrics.isEmpty()) {
            return@LaunchedEffect
        }
        val targetIndex = if (currentLineIndex in lyrics.indices) currentLineIndex else 0
        val isSongChanged = trackState.title != lastSongTitle

        if (isSongChanged) {
            lastSongTitle = trackState.title
            listState.scrollToItem(targetIndex, scrollOffset = 0)
            return@LaunchedEffect
        }

        val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
        if (visibleItem != null) {
            val itemCenter = visibleItem.offset.toFloat() + visibleItem.size.toFloat() / 2f
            val scrollDelta = itemCenter - focalCenterPx
            if (kotlin.math.abs(scrollDelta) > 1.0f) {
                listState.animateScrollBy(
                    value = scrollDelta,
                    animationSpec = lyricFluidScrollSpec
                )
            }
        } else {
            val estimatedHalfHeight = with(density) { 45.dp.toPx() }
            val targetOffset = (focalCenterPx - estimatedHalfHeight).roundToInt()
            listState.scrollToItem(targetIndex, scrollOffset = -targetOffset)
            val itemAfter = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            if (itemAfter != null) {
                val itemCenter = itemAfter.offset.toFloat() + itemAfter.size.toFloat() / 2f
                val scrollDelta = itemCenter - focalCenterPx
                if (kotlin.math.abs(scrollDelta) > 1.0f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = lyricFluidScrollSpec
                    )
                }
            }
        }
    }

    val isAlignLeft = lyricAlignment == AiPreferences.LYRIC_ALIGN_LEFT
    val listPadding = if (isAlignLeft) {
        PaddingValues(top = topFocalPadding, bottom = bottomFocalPadding, start = 20.dp, end = 48.dp)
    } else {
        PaddingValues(top = topFocalPadding, bottom = bottomFocalPadding, start = 24.dp, end = 24.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.07f to Color.Black,
                        0.72f to Color.Black,
                        0.95f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "${index}_${line.startTimeMs ?: 0L}_${line.original.hashCode()}" }
            ) { index, line ->
                val isActive = (index == currentLineIndex)
                val isFocal = (index == focalIndex)
                val distance = kotlin.math.abs(index - focalIndex)
                val annotation = annotatedLines[index]

                LyricLineRow(
                    line = line,
                    index = index,
                    focalIndex = focalIndex,
                    isActive = isActive,
                    isFocal = isFocal,
                    distance = distance,
                    isBrowsing = isBrowsing,
                    annotation = annotation,
                    isTraditional = isTraditional,
                    isDark = isDark,
                    isAmoledMode = isAmoledMode,
                    isWordByWordEnabled = isWordByWordEnabled,
                    currentPositionMsProvider = currentPositionMsProvider,
                    lyricAlignment = lyricAlignment,
                    furiganaMode = furiganaMode,
                    onClick = {
                        userScrolledAway = false
                        coroutineScope.launch {
                            val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                            if (visibleItem != null) {
                                val itemCenter = visibleItem.offset.toFloat() + visibleItem.size.toFloat() / 2f
                                val scrollDelta = itemCenter - focalCenterPx
                                if (kotlin.math.abs(scrollDelta) > 1.0f) {
                                    listState.animateScrollBy(scrollDelta, lyricFluidScrollSpec)
                                }
                            } else {
                                val estimatedHalfHeight = with(density) { 45.dp.toPx() }
                                val targetOffset = (focalCenterPx - estimatedHalfHeight).roundToInt()
                                listState.scrollToItem(index, scrollOffset = -targetOffset)
                                val itemAfter = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                if (itemAfter != null) {
                                    val itemCenter = itemAfter.offset.toFloat() + itemAfter.size.toFloat() / 2f
                                    val scrollDelta = itemCenter - focalCenterPx
                                    if (kotlin.math.abs(scrollDelta) > 1.0f) {
                                        listState.animateScrollBy(scrollDelta, lyricFluidScrollSpec)
                                    }
                                }
                            }
                        }
                        onLineClicked(line)
                    },
                    onAnnotationClick = {
                        if (annotation != null) {
                            val enriched = if (annotation.lyricTranslation.isNullOrBlank() && line.translation.isNotBlank()) {
                                annotation.copy(lyricTranslation = line.translation)
                            } else annotation
                            onAnnotationClicked(enriched)
                        }
                    }
                )
            }
        }

        // 浮动返回正在播放位置胶囊
        val showReturnPill = userScrolledAway && !isUserDragging && (visibleFocalIndex != currentLineIndex) && currentLineIndex in lyrics.indices && currentLineIndex >= 0
        AnimatedVisibility(
            visible = showReturnPill,
            enter = fadeIn(spring(dampingRatio = 0.86f, stiffness = 320f)) +
                    slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it / 2 } +
                    scaleIn(spring(dampingRatio = 0.86f, stiffness = 320f), initialScale = 0.90f),
            exit = fadeOut(tween(160)) +
                    slideOutVertically(tween(200)) { it / 2 } +
                    scaleOut(tween(160), targetScale = 0.90f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showPlaybackControls) 138.dp else 36.dp)
        ) {
            val isScrolledAbove = visibleFocalIndex < currentLineIndex
            Surface(
                color = if (isDark) {
                    if (isAmoledMode) Color(0xFF0C0C0E) else Color(0xFF1E202B).copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) {
                        if (isAmoledMode) Color(0xFF222226) else Color.White.copy(alpha = 0.16f)
                    } else {
                        Color.Black.copy(alpha = 0.10f)
                    }
                ),
                shadowElevation = 8.dp,
                modifier = Modifier.bouncyClickable(pressedScale = 0.93f) {
                    userScrolledAway = false
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isScrolledAbove) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = strings.returnToPlayingPosition,
                        tint = if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.returnToPlayingPosition,
                        color = if (isDark) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

/**
 * 单行双语歌词组件 (流体物理联动动画 + 苹果原生景深高斯模糊)
 */
@Composable
fun LyricLineRow(
    line: BilingualLyricLine,
    index: Int,
    focalIndex: Int,
    isActive: Boolean,
    isFocal: Boolean,
    distance: Int,
    isBrowsing: Boolean,
    annotation: LyricAnnotationEntity?,
    isTraditional: Boolean,
    isDark: Boolean,
    isAmoledMode: Boolean = false,
    isWordByWordEnabled: Boolean = true,
    currentPositionMsProvider: () -> Long = { 0L },
    lyricAlignment: Int = AiPreferences.LYRIC_ALIGN_LEFT,
    furiganaMode: FuriganaDisplayMode = FuriganaDisplayMode.OFF,
    onClick: () -> Unit,
    onAnnotationClick: () -> Unit
) {
    val isVisuallyActive = isActive

    val targetRowAlpha = when {
        isBrowsing -> {
            when (distance) {
                0 -> 0.96f
                1 -> if (isDark) 0.72f else 0.68f
                2 -> if (isDark) 0.48f else 0.42f
                else -> if (isDark) 0.28f else 0.22f
            }
        }
        isVisuallyActive -> 0.96f
        else -> when (distance) {
            0 -> 0.96f
            1 -> 0.45f
            else -> 0.18f
        }
    }

    val targetScale = when {
        isBrowsing -> when (distance) {
            0 -> 1.0f
            1 -> 0.985f
            else -> 0.975f
        }
        isVisuallyActive -> 1.0f
        else -> 0.985f
    }

    // Apple Music 官方标志性景深高斯模糊 (Depth of Field Bokeh Gaussian Blur)
    // 聚焦行 0dp 清晰纯净，相邻行 2.5dp 柔焦微虚，远距离行 6.5dp 磨砂焦外虚化
    val targetBlur = when {
        isBrowsing -> when (distance) {
            0 -> 0.dp
            1 -> 2.dp
            2 -> 4.dp
            else -> 6.5.dp
        }
        isVisuallyActive -> 0.dp
        else -> when (distance) {
            0 -> 0.dp
            1 -> 2.5.dp
            2 -> 5.dp
            else -> 7.5.dp
        }
    }

    // 独立条目流体弹簧/位移视差 (Fluid Vertical Parallax)
    val targetOffsetY = when {
        isVisuallyActive -> 0.dp
        distance == 1 -> if (index < focalIndex) (-3).dp else 3.dp
        else -> if (index < focalIndex) (-5).dp else 5.dp
    }

    val lyricHighlightAnimationSpec = remember {
        tween<Float>(
            durationMillis = 350,
            easing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1.0f)
        )
    }

    val lyricDpAnimationSpec = remember {
        tween<Dp>(
            durationMillis = 350,
            easing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1.0f)
        )
    }

    val animatedRowAlpha by animateFloatAsState(
        targetValue = targetRowAlpha,
        animationSpec = lyricHighlightAnimationSpec,
        label = "rowAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = lyricHighlightAnimationSpec,
        label = "lineScale"
    )

    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = lyricDpAnimationSpec,
        label = "lineBlur"
    )

    val animatedOffsetY by animateDpAsState(
        targetValue = targetOffsetY,
        animationSpec = lyricDpAnimationSpec,
        label = "lineOffsetY"
    )

    val displayOriginal = remember(line.original, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.original) else line.original
    }

    val displayTranslation = remember(line.translation, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.translation) else line.translation
    }

    if (displayOriginal.isBlank() && displayTranslation.isBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        return
    }

    val lyricColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
    val isAlignLeft = lyricAlignment == AiPreferences.LYRIC_ALIGN_LEFT
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val mainTextSize = if (isTablet) 24.sp else 34.sp
    val mainLineHeight = if (isTablet) 32.sp else 44.sp
    val subTextSize = if (isTablet) 17.sp else 22.sp
    val subLineHeight = if (isTablet) 22.sp else 28.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = animatedOffsetY)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedRowAlpha
                transformOrigin = if (isAlignLeft) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
            }
            .then(
                if (animatedBlur > 0.dp) Modifier.blur(radius = animatedBlur) else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .bouncyPress(
                interactionSource = interactionSource,
                pressedScale = 0.985f,
                pressedAlpha = 0.96f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                ),
                onClick = {
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (e: Exception) {}
                    onClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (isAlignLeft) Alignment.Start else Alignment.CenterHorizontally
    ) {
        val showFurigana = furiganaMode != FuriganaDisplayMode.OFF && FuriganaEngine.isJapanese(displayOriginal)
        if (showFurigana) {
            val segments = remember(displayOriginal) { FuriganaEngine.segmentText(displayOriginal) }
            val baseStyle = TextStyle(
                fontSize = mainTextSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = mainLineHeight,
                letterSpacing = (-0.5).sp,
                textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center
            )
            val rubyStyle = TextStyle(
                fontSize = if (isTablet) 11.sp else 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif
            )
            FuriganaText(
                segments = segments,
                mode = when (furiganaMode) {
                    FuriganaDisplayMode.ROMAJI -> FuriganaMode.ROMAJI
                    FuriganaDisplayMode.HIRAGANA -> FuriganaMode.HIRAGANA
                    else -> FuriganaMode.OFF
                },
                baseTextStyle = baseStyle,
                rubyTextStyle = rubyStyle,
                isActive = isVisuallyActive,
                activeColor = lyricColor,
                inactiveColor = lyricColor,
                horizontalArrangement = if (isAlignLeft) Arrangement.Start else Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isWordByWordEnabled && line.words.isNotEmpty()) {
            AppleMusicDualLayerLyricLine(
                text = displayOriginal,
                words = line.words,
                currentPositionMsProvider = currentPositionMsProvider,
                lyricColor = lyricColor,
                isAlignLeft = isAlignLeft,
                isTraditional = isTraditional,
                fontSize = mainTextSize,
                lineHeight = mainLineHeight
            )
        } else {
            Text(
                text = displayOriginal,
                color = lyricColor,
                fontSize = mainTextSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = mainLineHeight,
                letterSpacing = (-0.5).sp,
                textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (displayTranslation.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayTranslation,
                color = lyricColor.copy(alpha = if (isVisuallyActive) 0.88f else 0.72f),
                fontSize = subTextSize,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                lineHeight = subLineHeight,
                textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val showAnnotation = annotation != null && (isVisuallyActive || (isBrowsing && isFocal))
        AnimatedVisibility(
            visible = showAnnotation,
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
        ) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                LyricAnnotationBadge(
                    isActive = true,
                    isDark = isDark,
                    onClick = onAnnotationClick
                )
            }
        }
    }
}

/**
 * Apple Music 原生双层流光排版 (AMLL / 官方真机渲染标准)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppleMusicDualLayerLyricLine(
    text: String,
    words: List<LyricWord>,
    currentPositionMsProvider: () -> Long,
    lyricColor: Color,
    isAlignLeft: Boolean,
    isTraditional: Boolean = false,
    fontSize: TextUnit = 34.sp,
    lineHeight: TextUnit = 44.sp,
    modifier: Modifier = Modifier
) {
    val cleanWords = remember(words, isTraditional) {
        val cleaned = words.map {
            val raw = LyricWordParser.cleanInlineTimestamps(it.text)
            val converted = if (isTraditional) ChineseConverter.toTraditional(raw) else raw
            it.copy(text = converted)
        }.filter { it.text.isNotEmpty() }

        cleaned.mapIndexed { index, w ->
            val wText = w.text
            val isCurrentLatin = wText.isNotEmpty() && !wText.last().isWhitespace() && wText.last().code < 0x2E80
            val hasNextLatin = index < cleaned.size - 1 && cleaned[index + 1].text.isNotEmpty() && cleaned[index + 1].text.first().code < 0x2E80
            if (isCurrentLatin && hasNextLatin && !wText.endsWith(" ")) {
                w.copy(text = wText + " ")
            } else {
                w
            }
        }
    }

    if (cleanWords.isEmpty()) {
        Text(
            text = text,
            color = lyricColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = lineHeight,
            letterSpacing = (-0.5).sp,
            textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    FlowRow(
        horizontalArrangement = if (isAlignLeft) Arrangement.Start else Arrangement.Center,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        cleanWords.forEach { word ->
            AppleMusicWordUnit(
                word = word,
                currentPositionMsProvider = currentPositionMsProvider,
                lyricColor = lyricColor,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
        }
    }
}

@Composable
fun AppleMusicWordUnit(
    word: LyricWord,
    currentPositionMsProvider: () -> Long,
    lyricColor: Color,
    fontSize: TextUnit = 34.sp,
    lineHeight: TextUnit = 44.sp
) {
    val wordText = word.text
    val duration = word.durationMs.coerceAtLeast(1L)
    val start = word.startTimeMs
    val end = start + duration
    val density = LocalDensity.current
    // Apple Music 官方逆向 lyrics_karaoke_gradient_feather_width: 30dp
    val fadeWidth = remember(density) { with(density) { 30.dp.toPx() } }

    Box(contentAlignment = Alignment.TopStart) {
        Text(
            text = wordText,
            color = lyricColor.copy(alpha = 0.45f),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = lineHeight,
            letterSpacing = (-0.5).sp
        )

        Text(
            text = wordText,
            color = lyricColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = lineHeight,
            letterSpacing = (-0.5).sp,
            modifier = Modifier
                .graphicsLayer {
                    val now = currentPositionMsProvider()
                    compositingStrategy = if (now in start until end) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawWithContent {
                    val now = currentPositionMsProvider()
                    if (now < start) {
                        return@drawWithContent
                    }
                    if (now >= end) {
                        drawContent()
                        return@drawWithContent
                    }

                    val progress = ((now - start).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    val sweepX = size.width * progress

                    val brush = Brush.horizontalGradient(
                        0f to Color.White,
                        ((sweepX - fadeWidth * 0.4f) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) to Color.White,
                        ((sweepX + fadeWidth * 0.6f) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent,
                        startX = 0f,
                        endX = size.width
                    )

                    drawContent()
                    drawRect(
                        brush = brush,
                        size = size,
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}

/**
 * 珠宝级香槟金磨砂微胶囊典故徽标
 */
@Composable
fun LyricAnnotationBadge(
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    val infiniteTransition = rememberInfiniteTransition(label = "badgeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val badgeBg = if (isActive) {
        if (isDark) Color(0xFFFFD54F).copy(alpha = 0.16f) else Color(0xFFFFE082).copy(alpha = 0.35f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    }
    val badgeBorder = if (isActive) {
        if (isDark) Color(0xFFFFD54F).copy(alpha = glowAlpha) else Color(0xFFFFA000).copy(alpha = glowAlpha)
    } else {
        if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    }
    val badgeContent = if (isActive) {
        if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
    } else {
        if (isDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }

    Surface(
        color = badgeBg,
        shape = CircleShape,
        border = BorderStroke(
            0.5.dp,
            badgeBorder
        ),
        modifier = Modifier
            .bouncyClickable(
                pressedScale = 0.90f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = strings.triviaBadge,
                tint = badgeContent,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = strings.triviaBadge,
                color = badgeContent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.3.sp
            )
        }
    }
}
