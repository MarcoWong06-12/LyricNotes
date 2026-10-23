package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.presentation.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSongStorySheet(
    story: SongStoryEntity?,
    isTranslating: Boolean = false,
    isTraditional: Boolean = false,
    isAmoledMode: Boolean = false,
    onRetryTranslation: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (story == null) return

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
    val handleColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)
    val strings = LocalStrings.current

    val displayTitle = remember(story.title, isTraditional) {
        if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(story.title) else story.title
    }
    val displayArtist = remember(story.artist, isTraditional) {
        if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(story.artist) else story.artist
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        contentColor = sheetContent,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.5.dp))
            }
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        // 锁定 120Hz 高刷新率，消除弹窗滚动微卡顿
        LockWindowRefreshRateEffect()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. 顶部杂志标题区 (Editorial Header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Surface(
                        color = if (isDark) Color(0xFFFFB300).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "BEHIND THE SONG",
                            color = if (isDark) Color(0xFFFFCA28) else Color(0xFFD97706),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayTitle,
                        color = sheetContent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayArtist,
                        color = textSecondary,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyIconClickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.closeBtn,
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // 2. 专辑/单曲封面海报 (Hero Visual)
            if (!story.headerImageUrl.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = story.headerImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            // 3. 发行信息与制作人员 (Apple Grouped Inset Metadata Card)
            val hasReleaseDate = !story.releaseDate.isNullOrBlank()
            val hasProducer = !story.producerCredits.isNullOrBlank()
            if (hasReleaseDate || hasProducer) {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        story.releaseDate?.takeIf { it.isNotBlank() }?.let { date ->
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = strings.releaseDateLabel.uppercase(),
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.6.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Spacer(modifier = Modifier.height(2.5.dp))
                                Text(
                                    text = date,
                                    color = sheetContent,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                        story.producerCredits?.takeIf { it.isNotBlank() }?.let { prod ->
                            val displayProd = if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(prod) else prod
                            Column(modifier = Modifier.weight(2f, fill = false)) {
                                Text(
                                    text = strings.producerLabel.uppercase(),
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.6.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Spacer(modifier = Modifier.height(2.5.dp))
                                Text(
                                    text = displayProd,
                                    color = sheetContent,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 4. 深度考据内容区 (Editorial In-depth Section)
            Text(
                text = strings.songStoryDepthAnalysis,
                color = sheetContent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            // 精准期刊级段落双语对照视图
            BilingualContentView(
                originalText = story.descriptionPlain,
                translatedText = story.descriptionTranslation,
                isTranslating = isTranslating,
                isTraditional = isTraditional,
                isDark = isDark,
                onRetryTranslation = onRetryTranslation,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
