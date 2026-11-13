package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch

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

    val displayTitle = remember(story.title, isTraditional) {
        if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(story.title) else story.title
    }
    val displayArtist = remember(story.artist, isTraditional) {
        if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(story.artist) else story.artist
    }

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
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFFFCA28) else Color(0xFFB45309),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = strings.songStoryTitle,
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = displayTitle,
                        color = sheetContent,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.2).sp,
                        lineHeight = 27.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = displayArtist,
                        color = textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
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

            // 2. 专辑/单曲封面海报 (Hero Visual)
            if (!story.headerImageUrl.isNullOrBlank()) {
                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFE5E5EA),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AsyncImage(
                            model = story.headerImageUrl,
                            contentDescription = null,
                            alignment = Alignment.TopCenter,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = if (isDark) 0.40f else 0.22f)
                                        )
                                    )
                                )
                        )
                    }
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
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        story.releaseDate?.takeIf { it.isNotBlank() }?.let { date ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.releaseDateLabel.uppercase(),
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = date,
                                    color = sheetContent,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        if (hasReleaseDate && hasProducer) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp)
                                    .background(dividerColor)
                            )
                        }

                        story.producerCredits?.takeIf { it.isNotBlank() }?.let { prod ->
                            val displayProd = if (isTraditional) com.linernotes.app.core.util.ChineseConverter.toTraditional(prod) else prod
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.producerLabel.uppercase(),
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = displayProd,
                                    color = sheetContent,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 18.sp,
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
