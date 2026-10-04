package com.linernotes.app.presentation.floating

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.data.repository.NowPlayingData
import com.linernotes.app.presentation.common.bouncyIconClickable

/**
 * 桌面悬浮歌词组件 (Floating Lyrics Capsule)
 * 支持【网易云智能折行全显】与【单行跑马灯滚动】双模式，彻底解决长歌词与双语翻译截断问题
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
    capsuleWidthDp: Int = 356,
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

    val state = nowPlayingData.playbackState
    val lyrics = nowPlayingData.lyrics
    val activeIndex = nowPlayingData.currentLineIndex
    val isLoadingLyrics = nowPlayingData.isLoadingLyrics
    val currentLine = if (activeIndex in lyrics.indices) lyrics[activeIndex] else null

    // 稳定视觉骨架：副行仅在开启双语且有译文时优雅展示译文；无译文时单行居中展示，杜绝重复出现歌手名副标题
    val (originalText, secondaryText) = remember(currentLine, state.title, state.artist, state.hasValidTrack, isBilingual, isLoadingLyrics) {
        if (currentLine != null) {
            val orig = currentLine.original.ifBlank { state.title.ifBlank { "LyricNotes 桌面歌词" } }
            val trans = if (isBilingual && !currentLine.translation.isNullOrBlank()) {
                currentLine.translation
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
    val isLightColor = lyricColor.luminance() > 0.35f
    val secondaryColor = if (textColor != -1) {
        Color(textColor).copy(alpha = if (isLightColor) 0.85f else 0.75f)
    } else {
        Color.White.copy(alpha = 0.80f)
    }

    // 智能高对比度投影：根据前景色亮度自动选择深黑或雪白立体投影，绝不与壁纸或桌面图标混色
    val textShadow = Shadow(
        color = if (isLightColor) Color.Black.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.85f),
        offset = Offset(0f, 1.5f),
        blurRadius = 4f
    )

    // 双重防穿透底板：深色模式使用深黑 (0xFF12141C)，浅色文字自适应磨砂白 (0xFFF2F4F7)
    val effectiveBgAlpha = if (isLocked) (backgroundAlpha * 0.90f).coerceAtLeast(0.65f) else backgroundAlpha
    val baseBgColor = if (isLightColor) Color(0xFF12141C) else Color(0xFFF2F4F7)
    val capsuleBackground = baseBgColor.copy(alpha = effectiveBgAlpha)
    val primaryUiColor = if (isLightColor) Color.White else Color(0xFF1A1C24)

    val capsuleWidth = if (isExpanded) 356.dp else capsuleWidthDp.dp

    Surface(
        color = capsuleBackground,
        shape = if (isExpanded) RoundedCornerShape(22.dp) else RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (isLocked) 0.5.dp else 0.8.dp,
            color = if (isLightColor) Color.White.copy(alpha = if (isLocked) 0.15f else 0.28f)
                    else Color.Black.copy(alpha = if (isLocked) 0.15f else 0.25f)
        ),
        shadowElevation = if (isLocked) 2.dp else 10.dp,
        modifier = Modifier
            .width(capsuleWidth)
            .then(
                if (!isLocked) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = { onDragEnd(isExpanded) },
                            onDragCancel = { onDragEnd(isExpanded) }
                        )
                    }
                } else Modifier
            )
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            label = "capsuleExpansion"
        ) { expanded ->
            if (!expanded) {
                // ================= 1. 网易云经典全景歌词态 (Full Banner Capsule) =================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                        .then(
                            if (!isLocked) {
                                Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isExpanded = true
                                    onExpandChanged(true)
                                }
                            } else Modifier
                        )
                ) {
                    // 左侧微型黑胶唱片 / 封面图标
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

                    // 中间：当前歌词与翻译（支持网易云居中折行完整展示 或 单行跑马灯滚动）
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (displayMode == FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE) {
                            // 跑马灯单行平滑滚动模式（超长歌词自适应横向匀速滚动轮播）
                            Text(
                                text = originalText,
                                color = lyricColor,
                                fontSize = (13.5f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                style = TextStyle(shadow = textShadow),
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    delayMillis = 1500,
                                    initialDelayMillis = 1200,
                                    velocity = 32.dp
                                )
                            )

                            if (!secondaryText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = secondaryText,
                                    color = secondaryColor,
                                    fontSize = (11f * fontScale).sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    style = TextStyle(shadow = textShadow),
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        delayMillis = 1500,
                                        initialDelayMillis = 1200,
                                        velocity = 28.dp
                                    )
                                )
                            }
                        } else {
                            // 网易云经典居中折行全显模式 (默认推荐，完整呈现全部歌词与译文，绝不省略号截断)
                            Text(
                                text = originalText,
                                color = lyricColor,
                                fontSize = (13f * fontScale).sp,
                                lineHeight = (17.5f * fontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow)
                            )

                            if (!secondaryText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.5.dp))
                                Text(
                                    text = secondaryText,
                                    color = secondaryColor,
                                    fontSize = (11f * fontScale).sp,
                                    lineHeight = (15f * fontScale).sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
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
                    // 1. 顶栏：专辑封面 + 歌曲标题与歌手 (点击可直接唤醒主程序) + 快捷操作按钮群
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
                            // 封面图 (40.dp 圆角矩形)
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

                            // 歌曲标题与艺人
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.hasValidTrack) state.title else "LyricNotes",
                                    color = primaryUiColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (state.hasValidTrack && state.artist.isNotBlank()) {
                                    Text(
                                        text = state.artist,
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

                        // 锁定触摸穿透
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

                        // 收起卡片按钮
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

                        // 关闭悬浮窗
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

                    // 2. 中间：当前歌词与双语对照（严格高度约束，保证底栏永不被挤出）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp, max = 96.dp)
                    ) {
                        Text(
                            text = originalText,
                            color = lyricColor,
                            fontSize = (15f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = (20f * fontScale).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                        if (!secondaryText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = secondaryText,
                                color = secondaryColor,
                                fontSize = (12f * fontScale).sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = (16f * fontScale).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. 进度条微缩指示器
                    val currentProgress = if (state.durationMs > 0) {
                        (state.getEstimatedPositionMs().toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF1ED760),
                        trackColor = primaryUiColor.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. 底栏：大触控微型媒体播控控制器 (100% 永久可见，绝不被文字裁切)
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
