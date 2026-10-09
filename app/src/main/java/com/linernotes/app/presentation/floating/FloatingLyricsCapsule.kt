package com.linernotes.app.presentation.floating

import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.data.repository.NowPlayingData
import com.linernotes.app.presentation.common.bouncyIconClickable
import kotlinx.coroutines.delay

/**
 * 桌面悬浮歌词组件 (Floating Lyrics)
 * 1. 【纯净桌面歌词】(网易云经典风格 - 默认推荐)：彻底去除气泡边框与封面，纯歌词悬浮，超高对比度深色投影防重叠，轻触呼出微型快捷播控栏
 * 2. 【卡片胶囊模式】：带磨砂底板与专辑封面微缩图标，支持展开大播控卡片
 * 彻底消除 animateContentSize IPC 布局抖动，杜绝抽搐闪动 BUG
 */
@Composable
fun FloatingLyricsCapsule(
    nowPlayingData: NowPlayingData,
    isLocked: Boolean,
    isBilingual: Boolean,
    backgroundAlpha: Float,
    fontScale: Float,
    textColor: Int = -1,
    displayMode: Int = FloatingLyricsPreferences.DISPLAY_MODE_WRAP,
    floatingStyle: Int = FloatingLyricsPreferences.STYLE_PURE_LYRICS,
    capsuleWidthDp: Int = 356,
    isTraditional: Boolean = false,
    textAlignment: Int = FloatingLyricsPreferences.ALIGNMENT_CENTER,
    onDragStart: () -> Unit = {},
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: (isExpanded: Boolean) -> Unit,
    onExpandChanged: (isExpanded: Boolean) -> Unit = {},
    onToggleLock: () -> Unit,
    onToggleBilingual: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenApp: () -> Unit = {},
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    val state = nowPlayingData.playbackState
    val lyrics = nowPlayingData.lyrics
    val activeIndex = nowPlayingData.currentLineIndex
    val isLoadingLyrics = nowPlayingData.isLoadingLyrics
    val currentLine = if (activeIndex in lyrics.indices) lyrics[activeIndex] else null

    // 4秒无操作自动隐藏纯净模式快捷播控条（用户每次交互重置计时）
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000L)
            showControls = false
        }
    }

    // 锁定后立即关闭播控栏与展开卡片
    LaunchedEffect(isLocked) {
        if (isLocked) {
            showControls = false
            if (isExpanded) {
                isExpanded = false
                onExpandChanged(false)
            }
        }
    }

    // 稳定视觉文字数据
    val strings = com.linernotes.app.core.i18n.LocalStrings.current
    val (originalText, secondaryText) = remember(currentLine, state.title, state.artist, state.hasValidTrack, isBilingual, isLoadingLyrics, isTraditional, strings) {
        val (rawOrig, rawTrans) = if (currentLine != null) {
            val orig = currentLine.original.ifBlank { state.title.ifBlank { "${strings.appName} ${strings.desktopLyricsTitle}" } }
            val trans = if (isBilingual && !currentLine.translation.isNullOrBlank()) {
                currentLine.translation
            } else null
            orig to trans
        } else {
            if (state.hasValidTrack) {
                val orig = state.title.ifBlank { strings.nowPlayingPrefix }
                val trans = when {
                    isLoadingLyrics -> strings.syncingLyricsDots
                    state.artist.isNotBlank() -> state.artist
                    else -> strings.noScrollingLyrics
                }
                orig to trans
            } else {
                "${strings.appName} ${strings.desktopLyricsTitle}" to strings.noMusicPlaying
            }
        }
        val finalOrig = if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(rawOrig) else rawOrig
        val finalTrans = if (isTraditional && rawTrans != null) com.linernotes.app.core.util.ChineseConverter.toTraditional(rawTrans) else rawTrans
        finalOrig to finalTrans
    }

    val hasCover = !state.coverUrl.isNullOrBlank()

    val lyricColor = if (textColor != -1) Color(textColor) else Color.White
    val isLightColor = lyricColor.luminance() > 0.35f
    val secondaryColor = if (textColor != -1) {
        Color(textColor).copy(alpha = if (isLightColor) 0.95f else 0.90f)
    } else {
        Color.White.copy(alpha = 0.92f)
    }

    // 智能高清晰度边缘阴影：紧凑聚焦投影 (blurRadius = 3f, offset = 1.2f)，
    // 彻底去除过大高斯模糊造成的字体边缘发虚、发灰、模糊发蒙问题，在任何桌面壁纸与应用图标上均清晰锐利
    val textShadow = Shadow(
        color = if (isLightColor) Color.Black.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.85f),
        offset = Offset(0f, 1.2f),
        blurRadius = 3f
    )

    val baseBgColor = if (isLightColor) Color(0xFF12141C) else Color(0xFFF2F4F7)
    val primaryUiColor = if (isLightColor) Color.White else Color(0xFF1A1C24)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val safeMaxWidthDp = (screenWidthDp - 16).coerceAtLeast(240)
    val effectiveCapsuleWidthDp = (if (isExpanded) 356 else capsuleWidthDp).coerceAtMost(safeMaxWidthDp)
    val capsuleWidth = effectiveCapsuleWidthDp.dp

    val isAlignLeft = textAlignment == FloatingLyricsPreferences.ALIGNMENT_LEFT
    val contentAlignment = if (isAlignLeft) Alignment.Start else Alignment.CenterHorizontally
    val textAlignVal = if (isAlignLeft) TextAlign.Start else TextAlign.Center

    if (floatingStyle == FloatingLyricsPreferences.STYLE_PURE_LYRICS) {
        // =========================================================================
        // 【纯净桌面悬浮歌词】(网易云经典风格 - 默认)
        // 无底板无边框无封面，只有歌词悬浮，轻触呼出工具条，歌词锚定不跳动
        // =========================================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(capsuleWidth)
        ) {
            // 1. 纯净歌词文字主体 (锚定在顶部，轻触呼出下方微型工具条，拖拽移动，杜绝歌词跳动)
            Box(
                contentAlignment = if (isAlignLeft) Alignment.CenterStart else Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (backgroundAlpha > 0.05f) {
                            Modifier
                                .background(
                                    color = baseBgColor.copy(alpha = backgroundAlpha),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        } else {
                            Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        }
                    )
                    .then(
                        if (!isLocked) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isDrag = false
                                    var totalDrag = Offset.Zero
                                    val touchSlop = viewConfiguration.touchSlop

                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                            if (change.changedToUp()) {
                                                if (!isDrag) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    showControls = !showControls
                                                    if (showControls) {
                                                        lastInteractionTime = System.currentTimeMillis()
                                                    }
                                                } else {
                                                    onDragEnd(false)
                                                    isDrag = false
                                                }
                                                change.consume()
                                                break
                                            }

                                            val positionChange = change.positionChange()
                                            if (!isDrag) {
                                                totalDrag += positionChange
                                                if (totalDrag.getDistance() > touchSlop) {
                                                    isDrag = true
                                                    onDragStart()
                                                    change.consume()
                                                    if (positionChange != Offset.Zero) {
                                                        onDrag(positionChange.x, positionChange.y)
                                                    }
                                                }
                                            } else {
                                                change.consume()
                                                if (positionChange != Offset.Zero) {
                                                    onDrag(positionChange.x, positionChange.y)
                                                }
                                            }
                                        }
                                    } finally {
                                        if (isDrag) {
                                            onDragEnd(false)
                                        }
                                    }
                                }
                            }
                        } else Modifier
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = contentAlignment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (displayMode == FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE) {
                        key(originalText) {
                            Text(
                                text = originalText,
                                color = lyricColor,
                                fontSize = (16f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = textAlignVal,
                                maxLines = 1,
                                style = TextStyle(shadow = textShadow, letterSpacing = 0.sp),
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1200,
                                    repeatDelayMillis = 1500,
                                    velocity = 32.dp
                                )
                            )
                        }

                        if (!secondaryText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.5.dp))
                            key(secondaryText) {
                                Text(
                                    text = secondaryText,
                                    color = secondaryColor,
                                    fontSize = (13f * fontScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = textAlignVal,
                                    maxLines = 1,
                                    style = TextStyle(shadow = textShadow, letterSpacing = 0.sp),
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 1200,
                                        repeatDelayMillis = 1500,
                                        velocity = 28.dp
                                    )
                                )
                            }
                        }
                    } else {
                        // 经典折行全显模式 (无截断)
                        Text(
                            text = originalText,
                            color = lyricColor,
                            fontSize = (16f * fontScale).sp,
                            lineHeight = (22f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = textAlignVal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
                        )

                        if (!secondaryText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = secondaryText,
                                color = secondaryColor,
                                fontSize = (13f * fontScale).sp,
                                lineHeight = (18f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = textAlignVal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
                            )
                        }
                    }
                }
            }

            // 2. 微型快捷播控工具条 (轻触歌词在下方平滑展开，4秒无操作自动收起，GPU RenderNode 无抖动高刷渲染)
            AnimatedVisibility(
                visible = showControls && !isLocked,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(140)),
                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.92f, animationSpec = tween(100))
            ) {
                Surface(
                    color = Color(0xFF13151F).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.22f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // 上一曲
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastInteractionTime = System.currentTimeMillis()
                                onPrevious()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 播放 / 暂停
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastInteractionTime = System.currentTimeMillis()
                                onPlayPause()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF1ED760),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 下一曲
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastInteractionTime = System.currentTimeMillis()
                                onNext()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 分隔竖线
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(14.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.22f))
                        )

                        // 译文开关
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastInteractionTime = System.currentTimeMillis()
                                onToggleBilingual()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Bilingual",
                                tint = if (isBilingual) Color(0xFF03A9F4) else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 锁定穿透
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showControls = false
                                onToggleLock()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 打开应用
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastInteractionTime = System.currentTimeMillis()
                                onOpenApp()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 关闭悬浮窗
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showControls = false
                                onClose()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // 【卡片胶囊模式】(带封面与气泡底板，无 animateContentSize 抖动)
        // =========================================================================
        val effectiveBgAlpha = if (isLocked) (backgroundAlpha * 0.90f).coerceAtLeast(0.65f) else backgroundAlpha.coerceAtLeast(0.65f)
        val capsuleBackground = baseBgColor.copy(alpha = effectiveBgAlpha)

        Surface(
            color = capsuleBackground,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(
                width = if (isLocked) 0.5.dp else 0.8.dp,
                color = if (isLightColor) Color.White.copy(alpha = if (isLocked) 0.15f else 0.28f)
                else Color.Black.copy(alpha = if (isLocked) 0.15f else 0.25f)
            ),
            shadowElevation = if (isLocked) 2.dp else 10.dp,
            modifier = Modifier.width(capsuleWidth)
        ) {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(140)) togetherWith
                        fadeOut(animationSpec = tween(90)))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "capsuleExpansion"
            ) { expanded ->
                if (!expanded) {
                    // 胶囊紧凑态 (拖拽平滑移动，轻触展开大卡片，彻底消除误触冲突)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                            .then(
                                if (!isLocked) {
                                    Modifier.pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var isDrag = false
                                            var totalDrag = Offset.Zero
                                            val touchSlop = viewConfiguration.touchSlop

                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                    if (change.changedToUp()) {
                                                        if (!isDrag) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            isExpanded = true
                                                            onExpandChanged(true)
                                                        } else {
                                                            onDragEnd(false)
                                                            isDrag = false
                                                        }
                                                        change.consume()
                                                        break
                                                    }

                                                    val positionChange = change.positionChange()
                                                    if (!isDrag) {
                                                        totalDrag += positionChange
                                                        if (totalDrag.getDistance() > touchSlop) {
                                                            isDrag = true
                                                            onDragStart()
                                                            change.consume()
                                                            if (positionChange != Offset.Zero) {
                                                                onDrag(positionChange.x, positionChange.y)
                                                            }
                                                        }
                                                    } else {
                                                        change.consume()
                                                        if (positionChange != Offset.Zero) {
                                                            onDrag(positionChange.x, positionChange.y)
                                                        }
                                                    }
                                                }
                                            } finally {
                                                if (isDrag) {
                                                    onDragEnd(false)
                                                }
                                            }
                                        }
                                    }
                                } else Modifier
                            )
                    ) {
                        // 封面
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isLightColor) Color(0xFF22242B) else Color(0xFFE0E0E0))
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
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = contentAlignment,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (displayMode == FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE) {
                                key(originalText) {
                                    Text(
                                        text = originalText,
                                        color = lyricColor,
                                        fontSize = (14.5f * fontScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        textAlign = textAlignVal,
                                        maxLines = 1,
                                        style = TextStyle(shadow = textShadow, letterSpacing = 0.sp),
                                        modifier = Modifier.basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 1200,
                                            repeatDelayMillis = 1500,
                                            velocity = 32.dp
                                        )
                                    )
                                }

                                if (!secondaryText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    key(secondaryText) {
                                        Text(
                                            text = secondaryText,
                                            color = secondaryColor,
                                            fontSize = (12f * fontScale).sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.SansSerif,
                                            textAlign = textAlignVal,
                                            maxLines = 1,
                                            style = TextStyle(shadow = textShadow, letterSpacing = 0.sp),
                                            modifier = Modifier.basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                initialDelayMillis = 1200,
                                                repeatDelayMillis = 1500,
                                                velocity = 28.dp
                                            )
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = originalText,
                                    color = lyricColor,
                                    fontSize = (14f * fontScale).sp,
                                    lineHeight = (19f * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = textAlignVal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
                                )

                                if (!secondaryText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.5.dp))
                                    Text(
                                        text = secondaryText,
                                        color = secondaryColor,
                                        fontSize = (11.5f * fontScale).sp,
                                        lineHeight = (15.5f * fontScale).sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.SansSerif,
                                        textAlign = textAlignVal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
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
                    // 拓展大卡片态
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenApp()
                                    })
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isLightColor) Color(0xFF22242B) else Color(0xFFE0E0E0))
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
                                            tint = primaryUiColor.copy(alpha = 0.85f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (state.hasValidTrack) {
                                            if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(state.title) else state.title
                                        } else strings.appName,
                                        color = primaryUiColor,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.hasValidTrack && state.artist.isNotBlank()) {
                                        Text(
                                            text = if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(state.artist) else state.artist,
                                            color = primaryUiColor.copy(alpha = 0.65f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // 双语对照切换
                            Surface(
                                color = if (isBilingual) (if (isLightColor) Color(0xFF2E3345) else Color(0xFFD6E4F0)) else Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(28.dp)
                                    .bouncyIconClickable(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleBilingual()
                                    })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Bilingual",
                                        tint = if (isBilingual) Color(0xFF03A9F4) else primaryUiColor.copy(alpha = 0.60f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // 锁定
                            Surface(
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(28.dp)
                                    .bouncyIconClickable(onClick = {
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
                                        tint = primaryUiColor.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // 收起
                            Surface(
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(28.dp)
                                    .bouncyIconClickable(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isExpanded = false
                                        onExpandChanged(false)
                                    })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Collapse",
                                        tint = primaryUiColor.copy(alpha = 0.75f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // 关闭
                            Surface(
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(28.dp)
                                    .bouncyIconClickable(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClose()
                                    })
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

                        Column(
                            horizontalAlignment = contentAlignment,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = (110 * fontScale).dp)
                                .then(
                                    if (!isLocked) {
                                        Modifier.pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { onDragStart() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    onDrag(dragAmount.x, dragAmount.y)
                                                },
                                                onDragEnd = { onDragEnd(true) },
                                                onDragCancel = { onDragEnd(true) }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            Text(
                                text = originalText,
                                color = lyricColor,
                                fontSize = (16f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = textAlignVal,
                                lineHeight = (22f * fontScale).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
                            )
                            if (!secondaryText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = secondaryText,
                                    color = secondaryColor,
                                    fontSize = (13f * fontScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = textAlignVal,
                                    lineHeight = (18f * fontScale).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val currentPositionMs by produceState(
                            initialValue = state.getEstimatedPositionMs(),
                            key1 = state.isPlaying,
                            key2 = isExpanded
                        ) {
                            if (isExpanded && state.isPlaying) {
                                while (true) {
                                    value = state.getEstimatedPositionMs()
                                    delay(250L)
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = {
                                if (state.durationMs > 0) {
                                    (currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF1ED760),
                            trackColor = primaryUiColor.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPrevious()
                                    })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = primaryUiColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Surface(
                                color = if (isLightColor) Color.White else Color(0xFF1E2028),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(42.dp)
                                    .bouncyIconClickable(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPlayPause()
                                    })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = if (isLightColor) Color.Black else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Surface(
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(36.dp)
                                    .bouncyIconClickable(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNext()
                                    })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        tint = primaryUiColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
