package com.linernotes.app.presentation.booklet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.lyric.BilingualSentenceAligner
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.presentation.common.BouncyIconButton
import org.json.JSONArray

/**
 * 行内手风琴式歌词典故气泡卡片 (Inline Lyric Annotation Card)
 * 无需中断音乐与歌词视线，在歌词行正下方平滑展开背景典故解析
 */
@Composable
fun InlineLyricAnnotationCard(
    annotation: LyricAnnotationEntity,
    isTranslating: Boolean = false,
    isTraditional: Boolean = false,
    onTranslate: (LyricAnnotationEntity) -> Unit = {},
    onOpenFullSheet: () -> Unit = {},
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showOriginalText by remember { mutableStateOf(false) }

    val isChinese = remember(annotation.explanationText) {
        AiAnnotationCurator.isAlreadyChinese(annotation.explanationText)
    }

    // 解析图片 URL 列表
    val imageUrls = remember(annotation.imageUrlsJson) {
        val list = mutableListOf<String>()
        val json = annotation.imageUrlsJson
        if (!json.isNullOrBlank()) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val url = arr.optString(i)
                    if (url.isNotBlank()) list.add(url)
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        list
    }

    val hasTranslation = !annotation.explanationTranslation.isNullOrBlank()

    // 自动触发中文翻译：当没有翻译、原内容非中文且当前未在翻译中时，自动请求翻译
    LaunchedEffect(annotation.id, hasTranslation, isChinese) {
        if (!hasTranslation && !isChinese && !isTranslating) {
            onTranslate(annotation)
        }
    }

    // 决定当前展示的文本内容
    val rawText = if (hasTranslation && !showOriginalText) {
        annotation.explanationTranslation ?: annotation.explanationText
    } else {
        annotation.explanationText
    }

    val displayText = remember(rawText, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(rawText) else rawText
    }

    val lyricFragmentDisplay = remember(annotation.lyricFragment, isTraditional) {
        val converted = if (isTraditional) ChineseConverter.toTraditional(annotation.lyricFragment) else annotation.lyricFragment
        val lines = converted.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size > 2) {
            "${lines.take(2).joinToString(" / ")} … (${if (isTraditional) "共" else "共"} ${lines.size} ${if (isTraditional) "行" else "行"})"
        } else {
            lines.joinToString(" / ")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFFFD54F).copy(alpha = 0.55f),
                    Color(0xFFFFB300).copy(alpha = 0.25f),
                    Color.Transparent
                )
            )
        ),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. 顶部标头栏 (来源徽标、认证标示、操作按钮)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Genius 金色标识
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFD54F).copy(alpha = 0.20f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "G",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFD54F)
                                )
                            )
                        }
                    }

                    Text(
                        text = if (annotation.isVerified) {
                            if (isTraditional) "Genius 認證解析" else "Genius 认证解析"
                        } else {
                            if (isTraditional) "Genius 典故" else "Genius 典故"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    )

                    if (!annotation.authorName.isNullOrBlank()) {
                        Text(
                            text = "@${annotation.authorName}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    if (annotation.votesTotal > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "+${annotation.votesTotal}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 快捷操作工具组
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 翻译按钮
                    if (!isChinese || hasTranslation) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFD54F)
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (hasTranslation) {
                                            showOriginalText = !showOriginalText
                                        } else {
                                            onTranslate(annotation)
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = if (hasTranslation && !showOriginalText) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = when {
                                            hasTranslation && !showOriginalText -> if (isTraditional) "譯" else "译"
                                            hasTranslation && showOriginalText -> if (isTraditional) "原" else "原"
                                            else -> if (isTraditional) "翻譯" else "翻译"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (hasTranslation && !showOriginalText) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 展开完整 BottomSheet 详情按钮
                    BouncyIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenFullSheet()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Full details",
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // 收起按钮
                    BouncyIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCollapse()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 引述原句高亮横条
            if (lyricFragmentDisplay.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color(0xFFFFD54F).copy(alpha = 0.8f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "“$lyricFragmentDisplay”",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. 典故正文解析 (中英对照：中文一句话下面紧跟着英文原文，小小的不要占太多空间)
            if (!isChinese && hasTranslation && !showOriginalText) {
                val alignedParagraphs = remember(annotation.explanationText, annotation.explanationTranslation, isTraditional) {
                    val trans = if (isTraditional) ChineseConverter.toTraditional(annotation.explanationTranslation ?: "") else (annotation.explanationTranslation ?: "")
                    BilingualSentenceAligner.align(annotation.explanationText, trans)
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    alignedParagraphs.forEach { para ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            para.units.forEach { unit ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val zhText = if (isTraditional) ChineseConverter.toTraditional(unit.chinese) else unit.chinese
                                    if (zhText.isNotBlank()) {
                                        Text(
                                            text = zhText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.5.sp,
                                                lineHeight = 19.5.sp,
                                                letterSpacing = 0.2.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (unit.original.isNotBlank()) {
                                        Text(
                                            text = unit.original,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                letterSpacing = 0.1.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. 配图横向微缩画廊 (如有)
            if (imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imageUrls.forEach { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(80.dp)
                                .width(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { onOpenFullSheet() }
                        )
                    }
                }
            }
        }
    }
}
