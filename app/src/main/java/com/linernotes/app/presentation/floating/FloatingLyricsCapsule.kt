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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.lyric.LyricWordParser
import com.linernotes.app.core.playback.PlaybackClockState
import com.linernotes.app.core.playback.TrackMetadataState
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.repository.LyricsContentState
import com.linernotes.app.domain.model.LyricWord
import com.linernotes.app.presentation.common.bouncyIconClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 桌面悬浮歌词组件 (Floating Lyrics)
 * 1. 【纯净桌面歌词】：去边框、高对比投影防重叠、轻触呼出可横向滑动的微型快捷播控栏
 * 2. 【卡片胶囊模式】：带磨砂底板与专辑封面，支持展开大播控卡片
 * 彻底消除 WindowManager 尺寸/WRAP_CONTENT IPC 抖动，并支持省电离散逐字高亮
 */
@Composable
fun FloatingLyricsCapsule(
    trackMetadata: TrackMetadataState,
    playbackClock: PlaybackClockState,
    lyricsContent: LyricsContentState,
    activeIndex: Int,
    lyricOffsetMs: Long = 0L,
    isWordByWordEnabled: Boolean = true,
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
    onPureControlsChanged: (isVisible: Boolean) -> Unit = {},
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
    var prevLocked by remember { mutableStateOf(isLocked) }
    var showLockHint by remember { mutableStateOf(false) }

    val lyrics = lyricsContent.lyrics
    val isLoadingLyrics = lyricsContent.isLoadingLyrics
    val currentLine = if (activeIndex in lyrics.indices) lyrics[activeIndex] else null

    // 4秒无操作自动隐藏纯净模式快捷播控条（用户每次交互重置计时）
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000L)
            showControls = false
        }
    }

    LaunchedEffect(floatingStyle, showControls, isLocked) {
        onPureControlsChanged(floatingStyle == FloatingLyricsPreferences.STYLE_PURE_LYRICS && showControls && !isLocked)
    }

    DisposableEffect(Unit) {
        onDispose { onPureControlsChanged(false) }
    }

    // 锁定后立即关闭播控栏与展开卡片；由未锁定切为锁定时展示短暂解锁引导提示
    LaunchedEffect(isLocked) {
        if (isLocked) {
            showControls = false
            if (isExpanded) {
                isExpanded = false
                onExpandChanged(false)
            }
            if (!prevLocked) {
                showLockHint = true
                prevLocked = true
                delay(2600L)
                showLockHint = false
            }
        } else {
            prevLocked = false
            showLockHint = false
        }
    }

    // 稳定视觉文字数据
    val strings = LocalStrings.current
    val hasLineOriginal = currentLine != null && currentLine.original.isNotBlank()
    val (originalText, secondaryText) = remember(
        currentLine,
        trackMetadata.title,
        trackMetadata.artist,
        trackMetadata.hasValidTrack,
        isBilingual,
        isLoadingLyrics,
        isTraditional,
        strings
    ) {
        val (rawOrig, rawTrans) = if (currentLine != null) {
            val cleanedLineOrig = LyricWordParser.cleanInlineTimestamps(currentLine.original)
            val orig = cleanedLineOrig.ifBlank {
                trackMetadata.title.ifBlank { "${strings.appName} ${strings.desktopLyricsTitle}" }
            }
            val trans = if (isBilingual && currentLine.translation.isNotBlank()) {
                currentLine.translation
            } else null
            orig to trans
        } else {
            if (trackMetadata.hasValidTrack) {
                val orig = trackMetadata.title.ifBlank { strings.nowPlayingPrefix }
                val trans = when {
                    isLoadingLyrics -> strings.syncingLyricsDots
                    trackMetadata.artist.isNotBlank() -> trackMetadata.artist
                    else -> strings.noScrollingLyrics
                }
                orig to trans
            } else {
                "${strings.appName} ${strings.desktopLyricsTitle}" to strings.noMusicPlaying
            }
        }
        val finalOrig = if (isTraditional) ChineseConverter.toTraditional(rawOrig) else rawOrig
        val finalTrans = if (isTraditional && rawTrans != null) ChineseConverter.toTraditional(rawTrans) else rawTrans
        finalOrig to finalTrans
    }

    val wordMilestones = remember(currentLine, originalText, isWordByWordEnabled, isTraditional) {
        if (isWordByWordEnabled && hasLineOriginal && currentLine != null && currentLine.words.isNotEmpty()) {
            computeWordEndCharIndices(originalText, currentLine.words, isTraditional)
        } else {
            emptyList()
        }
    }

    // 仅在单词边界唤醒协程的离散逐字高亮，无需每帧重排或高频轮询
    var highlightedCharCount by remember(currentLine, wordMilestones, isWordByWordEnabled, originalText) {
        mutableStateOf<Int?>(null)
    }
    LaunchedEffect(
        currentLine,
        wordMilestones,
        isWordByWordEnabled,
        playbackClock.trackEpoch,
        playbackClock.isPlaying,
        playbackClock.currentPositionMs,
        playbackClock.lastUpdateTimeMs,
        playbackClock.playbackSpeed,
        lyricOffsetMs
    ) {
        if (!isWordByWordEnabled || wordMilestones.isEmpty()) {
            highlightedCharCount = null
            return@LaunchedEffect
        }
        val speed = playbackClock.playbackSpeed.coerceAtLeast(0.25f)
        while (isActive) {
            val effectivePos = (playbackClock.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
            var reachedIdx = -1
            for (i in wordMilestones.indices) {
                if (effectivePos >= wordMilestones[i].first) {
                    reachedIdx = i
                } else {
                    break
                }
            }
            highlightedCharCount = when {
                reachedIdx < 0 -> 0
                reachedIdx >= wordMilestones.lastIndex -> originalText.length
                else -> wordMilestones[reachedIdx].second
            }
            if (!playbackClock.isPlaying) break
            val nextMilestone = wordMilestones.getOrNull(reachedIdx + 1) ?: break
            val waitMs = ((nextMilestone.first - effectivePos).toDouble() / speed).toLong().coerceAtLeast(16L)
            delay(waitMs)
        }
    }

    val hasCover = !trackMetadata.coverUrl.isNullOrBlank()

    val lyricColor = if (textColor != -1) Color(textColor) else Color.White
    val isLightColor = lyricColor.luminance() > 0.35f
    val secondaryColor = if (textColor != -1) {
        Color(textColor).copy(alpha = if (isLightColor) 0.95f else 0.90f)
    } else {
        Color.White.copy(alpha = 0.92f)
    }

    // 紧凑聚焦投影，避免过大模糊在桌面图标上发灰
    val textShadow = Shadow(
        color = if (isLightColor) Color.Black.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.85f),
        offset = Offset(0f, 1.2f),
        blurRadius = 3f
    )

    val styledOriginal = remember(originalText, highlightedCharCount, lyricColor, isLightColor) {
        buildWordHighlightAnnotatedString(
            text = originalText,
            highlightedChars = highlightedCharCount,
            activeColor = lyricColor,
            dimmedColor = lyricColor.copy(alpha = if (isLightColor) 0.46f else 0.50f)
        )
    }

    val baseBgColor = if (isLightColor) Color(0xFF12141C) else Color(0xFFF2F4F7)
    val primaryUiColor = if (isLightColor) Color.White else Color(0xFF1A1C24)
    val configuration = LocalConfiguration.current
    val safeMaxWidthDp = (configuration.screenWidthDp - 16).coerceAtLeast(240)
    val effectiveCapsuleWidthDp = capsuleWidthDp.coerceIn(220, safeMaxWidthDp)
    val capsuleWidth = effectiveCapsuleWidthDp.dp

    val isAlignLeft = textAlignment == FloatingLyricsPreferences.ALIGNMENT_LEFT
    val contentAlignment = if (isAlignLeft) Alignment.Start else Alignment.CenterHorizontally
    val textAlignVal = if (isAlignLeft) TextAlign.Start else TextAlign.Center

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(capsuleWidth)
    ) {
        if (floatingStyle == FloatingLyricsPreferences.STYLE_PURE_LYRICS) {
            // =========================================================================
            // 【纯净桌面悬浮歌词】
            // =========================================================================
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
                    .floatingDragTapModifier(
                        enabled = !isLocked,
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showControls = !showControls
                            if (showControls) {
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        },
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = { onDragEnd(false) }
                    )
            ) {
                FloatingLyricTextBlock(
                    styledOriginal = styledOriginal,
                    originalKey = originalText,
                    secondaryText = secondaryText,
                    displayMode = displayMode,
                    lyricColor = lyricColor,
                    secondaryColor = secondaryColor,
                    textShadow = textShadow,
                    primaryFontSize = (16f * fontScale).sp,
                    primaryLineHeight = (22f * fontScale).sp,
                    secondaryFontSize = (13f * fontScale).sp,
                    secondaryLineHeight = (18f * fontScale).sp,
                    contentAlignment = contentAlignment,
                    textAlign = textAlignVal
                )
            }

            // 微型快捷播控工具条 (可横向滑动，预留底部阴影空间防裁剪)
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
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
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
                                contentDescription = strings.previousTrackDesc,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastInteractionTime = System.currentTimeMillis()
                                onPlayPause()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackClock.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackClock.isPlaying) strings.pauseDesc else strings.playDesc,
                                tint = Color(0xFF1ED760),
                                modifier = Modifier.size(20.dp)
                            )
                        }

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
                                contentDescription = strings.nextTrackDesc,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(14.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.22f))
                        )

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
                                contentDescription = strings.bilingualTabParallel,
                                tint = if (isBilingual) Color(0xFF03A9F4) else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

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
                                contentDescription = strings.floatingNotifLock,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

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
                                contentDescription = strings.topBarSettings,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

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
                                contentDescription = strings.closeBtn,
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // 【卡片胶囊模式】
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
                modifier = Modifier.fillMaxWidth()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                                .floatingDragTapModifier(
                                    enabled = !isLocked,
                                    onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isExpanded = true
                                        onExpandChanged(true)
                                    },
                                    onDragStart = onDragStart,
                                    onDrag = onDrag,
                                    onDragEnd = { onDragEnd(false) }
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isLightColor) Color(0xFF22242B) else Color(0xFFE0E0E0))
                            ) {
                                if (hasCover) {
                                    AsyncImage(
                                        model = trackMetadata.coverUrl,
                                        contentDescription = strings.albumCover,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = strings.desktopLyricsTitle,
                                        tint = lyricColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            FloatingLyricTextBlock(
                                styledOriginal = styledOriginal,
                                originalKey = originalText,
                                secondaryText = secondaryText,
                                displayMode = displayMode,
                                lyricColor = lyricColor,
                                secondaryColor = secondaryColor,
                                textShadow = textShadow,
                                primaryFontSize = if (displayMode == FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE) (14.5f * fontScale).sp else (14f * fontScale).sp,
                                primaryLineHeight = (19f * fontScale).sp,
                                secondaryFontSize = if (displayMode == FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE) (12f * fontScale).sp else (11.5f * fontScale).sp,
                                secondaryLineHeight = (15.5f * fontScale).sp,
                                contentAlignment = contentAlignment,
                                textAlign = textAlignVal,
                                modifier = Modifier.weight(1f)
                            )

                            if (!isLocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = strings.topBarMoreOptions,
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
                                                model = trackMetadata.coverUrl,
                                                contentDescription = strings.albumCover,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = strings.desktopLyricsTitle,
                                                tint = primaryUiColor.copy(alpha = 0.85f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (trackMetadata.hasValidTrack) {
                                                if (isTraditional) ChineseConverter.toTraditional(trackMetadata.title) else trackMetadata.title
                                            } else strings.appName,
                                            color = primaryUiColor,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (trackMetadata.hasValidTrack && trackMetadata.artist.isNotBlank()) {
                                            Text(
                                                text = if (isTraditional) ChineseConverter.toTraditional(trackMetadata.artist) else trackMetadata.artist,
                                                color = primaryUiColor.copy(alpha = 0.65f),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

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
                                            contentDescription = strings.bilingualTabParallel,
                                            tint = if (isBilingual) Color(0xFF03A9F4) else primaryUiColor.copy(alpha = 0.60f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

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
                                            contentDescription = strings.floatingNotifLock,
                                            tint = primaryUiColor.copy(alpha = 0.75f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

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
                                            contentDescription = strings.exitFullscreen,
                                            tint = primaryUiColor.copy(alpha = 0.75f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(2.dp))

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
                                            contentDescription = strings.closeBtn,
                                            tint = Color(0xFFFF8A80),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                contentAlignment = Alignment.Center,
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
                                FloatingLyricTextBlock(
                                    styledOriginal = styledOriginal,
                                    originalKey = originalText,
                                    secondaryText = secondaryText,
                                    displayMode = FloatingLyricsPreferences.DISPLAY_MODE_WRAP,
                                    lyricColor = lyricColor,
                                    secondaryColor = secondaryColor,
                                    textShadow = textShadow,
                                    primaryFontSize = (16f * fontScale).sp,
                                    primaryLineHeight = (22f * fontScale).sp,
                                    secondaryFontSize = (13f * fontScale).sp,
                                    secondaryLineHeight = (18f * fontScale).sp,
                                    contentAlignment = contentAlignment,
                                    textAlign = textAlignVal
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            var currentPositionMs by remember { mutableLongStateOf(playbackClock.getEstimatedPositionMs()) }
                            LaunchedEffect(
                                isExpanded,
                                playbackClock.trackEpoch,
                                playbackClock.isPlaying,
                                playbackClock.currentPositionMs,
                                playbackClock.lastUpdateTimeMs,
                                playbackClock.playbackSpeed
                            ) {
                                currentPositionMs = playbackClock.getEstimatedPositionMs()
                                if (isExpanded && playbackClock.isPlaying) {
                                    while (isActive) {
                                        withFrameMillis {
                                            currentPositionMs = playbackClock.getEstimatedPositionMs()
                                        }
                                    }
                                }
                            }

                            val durationMs = playbackClock.durationMs.takeIf { it > 0 } ?: trackMetadata.durationMs
                            LinearProgressIndicator(
                                progress = {
                                    if (durationMs > 0) {
                                        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
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
                                            contentDescription = strings.previousTrackDesc,
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
                                            imageVector = if (playbackClock.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (playbackClock.isPlaying) strings.pauseDesc else strings.playDesc,
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
                                            contentDescription = strings.nextTrackDesc,
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

        // 锁定触控穿透后短暂浮现解锁指引，避免用户不知如何恢复拖拽
        AnimatedVisibility(
            visible = showLockHint,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180)),
            exit = fadeOut(tween(240))
        ) {
            Surface(
                color = Color(0xFF13151F).copy(alpha = 0.90f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF03A9F4),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = strings.floatingLockedHint,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingLyricTextBlock(
    styledOriginal: AnnotatedString,
    originalKey: String,
    secondaryText: String?,
    displayMode: Int,
    lyricColor: Color,
    secondaryColor: Color,
    textShadow: Shadow,
    primaryFontSize: TextUnit,
    primaryLineHeight: TextUnit,
    secondaryFontSize: TextUnit,
    secondaryLineHeight: TextUnit,
    contentAlignment: Alignment.Horizontal,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = contentAlignment,
        modifier = modifier.fillMaxWidth()
    ) {
        if (displayMode == FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE) {
            key(originalKey) {
                Text(
                    text = styledOriginal,
                    color = lyricColor,
                    fontSize = primaryFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = textAlign,
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
                        fontSize = secondaryFontSize,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = textAlign,
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
                text = styledOriginal,
                color = lyricColor,
                fontSize = primaryFontSize,
                lineHeight = primaryLineHeight,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
            )
            if (!secondaryText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = secondaryText,
                    color = secondaryColor,
                    fontSize = secondaryFontSize,
                    lineHeight = secondaryLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = textAlign,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = textShadow, letterSpacing = 0.sp)
                )
            }
        }
    }
}

private fun Modifier.floatingDragTapModifier(
    enabled: Boolean,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
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
                            onTap()
                        } else {
                            onDragEnd()
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
                    onDragEnd()
                }
            }
        }
    }
}

private fun computeWordEndCharIndices(
    displayOriginal: String,
    words: List<LyricWord>,
    isTraditional: Boolean
): List<Pair<Long, Int>> {
    if (displayOriginal.isEmpty() || words.isEmpty()) return emptyList()
    var cursor = 0
    val milestones = ArrayList<Pair<Long, Int>>(words.size)
    for (w in words) {
        val raw = LyricWordParser.cleanInlineTimestamps(w.text)
        val converted = if (isTraditional) ChineseConverter.toTraditional(raw) else raw
        val token = converted.trim()
        if (token.isNotEmpty()) {
            val matchIdx = displayOriginal.indexOf(token, startIndex = cursor, ignoreCase = true)
            if (matchIdx >= 0) {
                cursor = matchIdx + token.length
                while (cursor < displayOriginal.length && displayOriginal[cursor].isWhitespace()) {
                    cursor++
                }
            }
        }
        milestones.add(w.startTimeMs to cursor.coerceIn(0, displayOriginal.length))
    }
    return milestones
}

private fun buildWordHighlightAnnotatedString(
    text: String,
    highlightedChars: Int?,
    activeColor: Color,
    dimmedColor: Color
): AnnotatedString {
    if (highlightedChars == null || text.isEmpty()) {
        return AnnotatedString(text)
    }
    val splitIndex = highlightedChars.coerceIn(0, text.length)
    return buildAnnotatedString {
        if (splitIndex > 0) {
            withStyle(SpanStyle(color = activeColor)) {
                append(text.substring(0, splitIndex))
            }
        }
        if (splitIndex < text.length) {
            withStyle(SpanStyle(color = dimmedColor)) {
                append(text.substring(splitIndex))
            }
        }
    }
}
