package com.linernotes.app.presentation.floating

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleBilingual: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // 展开态闲置 5 秒后自动收缩回极简胶囊
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(5000L)
            isExpanded = false
        }
    }

    val state = nowPlayingData.playbackState
    val lyrics = nowPlayingData.lyrics
    val activeIndex = nowPlayingData.currentLineIndex
    val currentLine = if (activeIndex in lyrics.indices) lyrics[activeIndex] else null

    val originalText = currentLine?.original?.ifBlank { null }
        ?: if (state.hasValidTrack) state.title else "LyricNotes 桌面歌词"
    val translationText = currentLine?.translation?.ifBlank { null }

    val hasCover = !state.coverUrl.isNullOrBlank()

    // 基础毛玻璃深色背板：锁定状态下背景更加清透，非锁定时饱满沉稳
    val effectiveBgAlpha = if (isLocked) (backgroundAlpha * 0.65f).coerceAtLeast(0.25f) else backgroundAlpha
    val capsuleBackground = Color(0xFF101114).copy(alpha = effectiveBgAlpha)

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.85f),
        offset = Offset(0f, 1.5f),
        blurRadius = 3f
    )

    Surface(
        color = capsuleBackground,
        shape = if (isExpanded) RoundedCornerShape(22.dp) else RoundedCornerShape(26.dp),
        border = BorderStroke(
            width = if (isLocked) 0.4.dp else 0.6.dp,
            color = Color.White.copy(alpha = if (isLocked) 0.12f else 0.22f)
        ),
        shadowElevation = if (isLocked) 2.dp else 8.dp,
        modifier = Modifier
            .widthIn(min = 220.dp, max = 360.dp)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 650f)
            )
            .then(
                if (!isLocked) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = onDragEnd
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
                    .padding(horizontal = 9.dp, vertical = 7.dp)
                    .then(
                        if (!isLocked) {
                            Modifier.clickable { isExpanded = true }
                        } else Modifier
                    )
            ) {
                // 左侧微型黑胶唱片 / 封面图标
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
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
                            tint = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(9.dp))

                // 中间：当前歌词（单行或双语对照）
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // 原文行
                    AnimatedContent(
                        targetState = originalText,
                        transitionSpec = { fadeIn(spring(stiffness = 500f)) togetherWith fadeOut(spring(stiffness = 500f)) },
                        label = "floatingOriginal"
                    ) { targetOrig ->
                        Text(
                            text = targetOrig,
                            color = Color.White,
                            fontSize = (13.5f * fontScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }

                    // 翻译行（若启用双语且存在翻译）
                    if (isBilingual && !translationText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = translationText,
                            transitionSpec = { fadeIn(spring(stiffness = 500f)) togetherWith fadeOut(spring(stiffness = 500f)) },
                            label = "floatingTranslation"
                        ) { targetTrans ->
                            Text(
                                text = targetTrans,
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
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
                        tint = Color.White.copy(alpha = 0.50f),
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

                    Spacer(modifier = Modifier.width(6.dp))

                    // 穿透锁定按钮
                    Surface(
                        color = Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .bouncyIconClickable(onClick = {
                                onToggleLock()
                                isExpanded = false
                            })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LockOutline,
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
                            .bouncyIconClickable(onClick = { isExpanded = false })
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

                // 中间：当前歌词高光
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = originalText,
                        color = Color.White,
                        fontSize = (14.5f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = textShadow)
                    )
                    if (isBilingual && !translationText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = translationText,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                            maxLines = 2,
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

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
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

                    Spacer(modifier = Modifier.width(16.dp))

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
