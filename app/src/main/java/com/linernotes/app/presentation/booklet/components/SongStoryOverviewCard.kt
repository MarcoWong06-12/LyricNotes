package com.linernotes.app.presentation.booklet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.presentation.common.BouncyTonalButton
import com.linernotes.app.presentation.common.bouncyClickable

@Composable
fun SongStoryOverviewCard(
    story: SongStoryEntity?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isTranslating: Boolean = false,
    isTraditional: Boolean = false,
    onTranslate: (SongStoryEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (story == null || story.descriptionPlain.isBlank()) return

    val isChinese = remember(story.descriptionPlain) {
        AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)
    }

    // 自动触发背景故事中文翻译：当未翻译且原内容非中文时，自动请求翻译
    LaunchedEffect(story.trackId, story.descriptionTranslation, isChinese) {
        if (!isChinese && story.descriptionTranslation.isNullOrBlank() && !isTranslating) {
            onTranslate(story)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .bouncyClickable(pressedScale = 0.98f) { onToggleExpand() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部横幅图片 (若有 headerImageUrl)
            if (!story.headerImageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    AsyncImage(
                        model = story.headerImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // 标题行与折叠图标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isTraditional) "關於《${story.title}》· 創作心境與背景" else "关于《${story.title}》· 创作心境与背景",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!story.releaseDate.isNullOrBlank() || !story.producerCredits.isNullOrBlank()) {
                                val metaText = listOfNotNull(
                                    story.releaseDate?.let { if (isTraditional) "發行於 $it" else "发行于 $it" },
                                    story.producerCredits?.let { if (isTraditional) "製作: $it" else "制作: $it" }
                                ).joinToString(" • ")
                                Text(
                                    text = metaText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isExpanded) {
                    // 展开状态：使用段落级双语对照阅读视图
                    BilingualContentView(
                        originalText = story.descriptionPlain,
                        translatedText = story.descriptionTranslation,
                        isTranslating = isTranslating,
                        isTraditional = isTraditional,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 若未翻译（如网络超时），提供一键重试翻译按钮
                    if (!isChinese && story.descriptionTranslation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        BouncyTonalButton(
                            onClick = { onTranslate(story) },
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isTranslating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTraditional) "正在翻譯背景故事..." else "正在翻译背景故事...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTraditional) "一鍵生成中文對照" else "一键生成中文对照", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // 折叠状态：显示前 3 行预览（优先显示中文翻译）
                    val previewText = story.descriptionTranslation?.takeIf { it.isNotBlank() } ?: story.descriptionPlain
                    val displayPreview = if (isTraditional) ChineseConverter.toTraditional(previewText) else previewText
                    Text(
                        text = displayPreview,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
