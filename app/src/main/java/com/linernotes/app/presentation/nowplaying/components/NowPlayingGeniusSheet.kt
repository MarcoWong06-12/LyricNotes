package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.presentation.common.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingGeniusSheet(
    annotation: LyricAnnotationEntity?,
    lyricTranslation: String? = null,
    isTranslating: Boolean = false,
    isTraditional: Boolean = false,
    isAmoledMode: Boolean = false,
    onRetryTranslation: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (annotation == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismissWithAnimation: () -> Unit = {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isPureAmoled = isDark && isAmoledMode

    // Apple Editorial & Grouped Inset 配色体系
    val sheetBg = if (isDark) {
        if (isPureAmoled) Color.Black else Color(0xFF121214)
    } else Color(0xFFF2F2F7)

    val cardBg = if (isDark) {
        if (isPureAmoled) Color(0xFF0C0C0E) else Color(0xFF1C1C1E)
    } else Color.White

    val cardBorder = if (isDark) {
        if (isPureAmoled) Color(0xFF222226) else Color.White.copy(alpha = 0.08f)
    } else Color.Black.copy(alpha = 0.05f)

    val sheetContent = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.48f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)
    val strings = LocalStrings.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = sheetContent,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.5.dp))
            }
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LockWindowRefreshRateEffect()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部认证条与关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isDark) Color(0xFFFFC107).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.10f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        0.5.dp,
                        if (isDark) Color(0xFFFFC107).copy(alpha = 0.36f) else Color(0xFFD97706).copy(alpha = 0.28f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFFFCA28) else Color(0xFFB45309),
                            modifier = Modifier.size(13.5.dp)
                        )
                        Text(
                            text = if (annotation.isVerified) strings.geniusVerified else strings.geniusAnnotation,
                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isTranslating) {
                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(32.dp)
                                .bouncyIconClickable(onClick = onRetryTranslation)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = strings.retryBtn,
                                    tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1C1C1E).copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .bouncyIconClickable(onClick = dismissWithAnimation)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.closeBtn,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1C1C1E).copy(alpha = 0.75f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 被引用的歌词原句卡片 (Quoted Lyric Card: 原文下紧随中文翻译)
            val displayFrag = (if (isTraditional) ChineseConverter.toTraditional(annotation.lyricFragment) else annotation.lyricFragment).trim()
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "“$displayFrag”",
                            color = sheetContent,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 23.sp
                        )

                        val displayLyricTrans = lyricTranslation?.takeIf { it.isNotBlank() } ?: annotation.lyricTranslation
                        if (!displayLyricTrans.isNullOrBlank()) {
                            val finalTrans = (if (isTraditional) ChineseConverter.toTraditional(displayLyricTrans) else displayLyricTrans).trim()
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = finalTrans,
                                color = if (isDark) Color.White.copy(alpha = 0.78f) else Color(0xFF1C1C1E).copy(alpha = 0.72f),
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = strings.geniusDepthAnalysis,
                color = sheetContent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 精准句对句/段对段双语对照视图
            BilingualContentView(
                originalText = annotation.explanationText,
                translatedText = annotation.explanationTranslation,
                isTranslating = isTranslating,
                isTraditional = isTraditional,
                isDark = isDark,
                onRetryTranslation = onRetryTranslation,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 底部贡献者信息与点赞 (Apple Grouped Inset Card Row)
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!annotation.authorAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = annotation.authorAvatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFFFFC107).copy(alpha = 0.18f) else Color(0xFFF59E0B).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                        Text(
                            text = annotation.authorName ?: strings.geniusAnnotation,
                            color = textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (annotation.votesTotal > 0) {
                        val upvoteColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309)
                        Surface(
                            color = if (isDark) Color(0xFFFFC107).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.10f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    tint = upvoteColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${annotation.votesTotal} ${strings.votesCount}",
                                    color = upvoteColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
