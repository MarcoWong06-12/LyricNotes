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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 丝滑流畅歌词滚动列表 (流体视差与羽化遮罩)
 * 1. 采用物理级几何中心对齐：动态计算歌词行垂直中点与视口黄金分割线 (36%) 的精确差值
 * 2. 硬件级 direct 光栅化渲染，剔除全屏离屏蒙版拷贝开销
 * 3. 活跃行 28sp 纯白 Bold 聚光灯聚焦，非活跃行 20sp 半透柔光退居次席
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
    // Apple Music 官方视口边距：活跃行顶端基线严格锚定在视口偏上部约 22% 黄金阅读带 (约 170dp)
    val focalTopDp = (screenHeightDp * 0.22f).coerceIn(160.dp, 195.dp)
    val focalTopPx = with(density) { focalTopDp.toPx() }
    val topFocalPadding = focalTopDp
    val bottomFocalPadding = screenHeightDp * 0.72f

    // 临界阻尼柔性弹簧 (dampingRatio = 0.96f, stiffness = 145f)
    val lyricFluidSpring = remember {
        spring<Float>(
            dampingRatio = 0.96f,
            stiffness = 145f
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

    // 动态计算当前落在黄金视觉带最中央的歌词行索引
    val visibleFocalIndex by remember(currentLineIndex, focalTopPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf (if (currentLineIndex in lyrics.indices) currentLineIndex else 0)
            val closest = visibleItems.minByOrNull { item ->
                kotlin.math.abs(item.offset.toFloat() - focalTopPx)
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

    // Apple Music 官方视线重心动力学 (22% 视口顶端基线锚定 + 临界阻尼物理弹簧)
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
            val scrollDelta = visibleItem.offset.toFloat() - focalTopPx
            if (kotlin.math.abs(scrollDelta) > 1.5f) {
                listState.animateScrollBy(
                    value = scrollDelta,
                    animationSpec = lyricFluidSpring
                )
            }
        } else {
            listState.scrollToItem(targetIndex, scrollOffset = 0)
            val itemAfter = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            if (itemAfter != null) {
                val scrollDelta = itemAfter.offset.toFloat() - focalTopPx
                if (kotlin.math.abs(scrollDelta) > 1.5f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = lyricFluidSpring
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
                        0.06f to Color.Black,
                        0.76f to Color.Black,
                        0.96f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(18.dp),
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
                                val scrollDelta = visibleItem.offset.toFloat() - focalTopPx
                                if (kotlin.math.abs(scrollDelta) > 1.5f) {
                                    listState.animateScrollBy(scrollDelta, lyricFluidSpring)
                                }
                            } else {
                                listState.scrollToItem(index, scrollOffset = 0)
                                val itemAfter = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                if (itemAfter != null) {
                                    val scrollDelta = itemAfter.offset.toFloat() - focalTopPx
                                    if (kotlin.math.abs(scrollDelta) > 1.5f) {
                                        listState.animateScrollBy(scrollDelta, lyricFluidSpring)
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
 * 单行双语歌词组件 (流体物理联动动画)
 */
@Composable
fun LyricLineRow(
    line: BilingualLyricLine,
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
                0 -> 1.0f
                1 -> if (isDark) 0.85f else 0.82f
                2 -> if (isDark) 0.65f else 0.62f
                else -> if (isDark) 0.45f else 0.42f
            }
        }
        isVisuallyActive -> 1.0f
        else -> when (distance) {
            0 -> if (isDark) 0.88f else 0.84f
            1 -> if (isDark) 0.46f else 0.42f
            2 -> if (isDark) 0.26f else 0.24f
            else -> if (isDark) 0.14f else 0.15f
        }
    }

    val targetScale = when {
        isBrowsing -> when (distance) {
            0 -> 1.06f
            1 -> 0.98f
            2 -> 0.94f
            else -> 0.90f
        }
        isVisuallyActive -> 1.08f
        else -> when (distance) {
            0 -> 1.00f
            1 -> 0.96f
            2 -> 0.92f
            else -> 0.88f
        }
    }

    val lyricAlphaSpring = remember {
        spring<Float>(
            dampingRatio = 0.96f,
            stiffness = 145f
        )
    }

    val lyricScaleSpring = remember {
        spring<Float>(
            dampingRatio = 0.96f,
            stiffness = 145f
        )
    }

    val animatedRowAlpha by animateFloatAsState(
        targetValue = targetRowAlpha,
        animationSpec = lyricAlphaSpring,
        label = "rowAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = lyricScaleSpring,
        label = "lineScale"
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedRowAlpha
                transformOrigin = if (isAlignLeft) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
            }
            .clip(RoundedCornerShape(14.dp))
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = if (isAlignLeft) Alignment.Start else Alignment.CenterHorizontally
    ) {
        val showFurigana = furiganaMode != FuriganaDisplayMode.OFF && FuriganaEngine.isJapanese(displayOriginal)
        if (showFurigana) {
            val segments = remember(displayOriginal) { FuriganaEngine.segmentText(displayOriginal) }
            val baseStyle = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 38.sp,
                letterSpacing = (-0.5).sp,
                textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center
            )
            val rubyStyle = TextStyle(
                fontSize = 13.5.sp,
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
                isTraditional = isTraditional
            )
        } else {
            Text(
                text = displayOriginal,
                color = lyricColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 38.sp,
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 22.sp,
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
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 38.sp,
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
                lyricColor = lyricColor
            )
        }
    }
}

@Composable
fun AppleMusicWordUnit(
    word: LyricWord,
    currentPositionMsProvider: () -> Long,
    lyricColor: Color
) {
    val wordText = word.text
    val duration = word.durationMs.coerceAtLeast(1L)
    val start = word.startTimeMs
    val end = start + duration
    val density = LocalDensity.current
    val fadeWidth = remember(density) { with(density) { 14.dp.toPx() } }

    Box(contentAlignment = Alignment.TopStart) {
        Text(
            text = wordText,
            color = lyricColor.copy(alpha = 0.50f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp
        )

        Text(
            text = wordText,
            color = lyricColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 38.sp,
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
