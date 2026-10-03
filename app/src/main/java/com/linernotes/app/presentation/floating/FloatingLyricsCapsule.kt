package com.linernotes.app.presentation.floating

import androidx.compose.animation.*
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

/**
 * Apple 灵动岛 / 桌面胶囊风格的悬浮歌词组件 (Floating Lyrics Capsule)
 * 具备双单行紧凑防挤压排版、展开态完整播控底栏、防桌面穿透的高质感磨砂背板与智能反差投影
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

    val state = nowPlayingData.playbackState
    val lyrics = nowPlayingData.lyrics
    val activeIndex = nowPlayingData.currentLineIndex
    val isLoadingLyrics = nowPlayingData.isLoadingLyrics
    val currentLine = if (activeIndex in lyrics.indices) lyrics[activeIndex] else null

    // 稳定视觉骨架：副行优雅展示双语译文或艺人/状态信息，绝不出现空行抖动
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
    val secondaryColor = if (textColor != -1) Color(textColor).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.80f)

    // 智能高对比度投影：根据前景色亮度自动选择深黑或雪白立体投影，绝不与壁纸或桌面图标混色
    val isLightColor = lyricColor.luminance() > 0.40f
    val textShadow = Shadow(
        color = if (isLightColor) Color.Black.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.90f),
        offset = Offset(0f, 1.5f),
        blurRadius = 4f
    )

    // 双重防穿透深色底板：锁定状态下保留微透，常规状态下保障高对比度遮蔽下层图标
    val effectiveBgAlpha = if (isLocked) (backgroundAlpha * 0.90f).coerceAtLeast(0.65f) else backgroundAlpha
    val capsuleBackground = Color(0xFF12141C).copy(alpha = effectiveBgAlpha)

    Surface(
        color = capsuleBackground,
        shape = if (isExpanded) RoundedCornerShape(22.dp) else RoundedCornerShape(26.dp),
        border = BorderStroke(
            width = if (isLocked) 0.5.dp else 0.8.dp,
            color = if (isLightColor) Color.White.copy(alpha = if (isLocked) 0.15f else 0.28f)
                    else Color.Black.copy(alpha = if (isLocked) 0.25f else 0.40f)
        ),
        shadowElevation = if (isLocked) 2.dp else 10.dp,
        modifier = Modifier
            .widthIn(min = 280.dp, max = if (isExpanded) 380.dp else 340.dp)
            .then(
                if (!isLocked) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
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
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            label = "capsuleExpansion"
        ) { expanded ->
            if (!expanded) {
                // ================= 1. 极简灵动胶囊态 (Compact Capsule) =================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .then(
                            if (!isLocked) {
                                Modifier.clickable {
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

                    // 中间：当前歌词（原文单行 + 译文单行，居中对齐，严禁多行挤压堆叠）
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 原文单行
                        Text(
                            text = originalText,
                            color = lyricColor,
                            fontSize = (13.5f * fontScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )

                        // 译文单行（若存在译文，单行居中展示；如无译文则优雅不占位）
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
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow)
                            )
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
                    // 1. 顶栏：专辑封面 + 歌曲标题与歌手 + 快捷操作按钮群
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 封面图 (40.dp 圆角矩形)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 歌曲标题与艺人
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.hasValidTrack) state.title else "LyricNotes",
                                color = Color.White,
                                fontSize = 13.5.sp,
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

                        // 双语对照切换
                        Surface(
                            color = if (isBilingual) Color(0xFF2E3345) else Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(28.dp)
                                .bouncyIconClickable(onClick = onToggleBilingual)
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
                                    tint = Color.White.copy(alpha = 0.75f),
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

                        Spacer(modifier = Modifier.width(2.dp))

                        // 关闭悬浮窗
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
                        trackColor = Color.White.copy(alpha = 0.15f)
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
                                .bouncyIconClickable(onClick = onPrevious)
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

                        Spacer(modifier = Modifier.width(20.dp))

                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(42.dp)
                                .bouncyIconClickable(onClick = onPlayPause)
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

                        Spacer(modifier = Modifier.width(20.dp))

                        Surface(
                            color = Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(36.dp)
                                .bouncyIconClickable(onClick = onNext)
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
}
