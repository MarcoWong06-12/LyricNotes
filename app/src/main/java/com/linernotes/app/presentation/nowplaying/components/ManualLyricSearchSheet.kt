package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.domain.model.LyricCandidateItem
import com.linernotes.app.domain.model.LyricSource
import com.linernotes.app.presentation.common.bouncyClickable

/**
 * 手动搜歌词与版本切换器抽屉
 * 允许用户在网易云与 QQ 音乐海量版本库中自由搜索、挑选并绑定最满意的歌词与翻译版本
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLyricSearchSheet(
    trackTitle: String,
    artistName: String,
    targetDurationMs: Long,
    candidates: List<LyricCandidateItem>,
    isSearching: Boolean,
    isApplying: Boolean,
    hasManualBinding: Boolean,
    errorMessage: String?,
    isTraditional: Boolean,
    onSearch: (String) -> Unit,
    onSelectCandidate: (LyricCandidateItem) -> Unit,
    onResetToAutoMatch: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    var inputQuery by remember { mutableStateOf(if (trackTitle.isNotBlank()) "$trackTitle $artistName".trim() else "") }
    var selectedFilterSource by remember { mutableStateOf<LyricSource?>(null) } // null = 全部

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetBg = if (isDark) Color(0xFF13141B) else MaterialTheme.colorScheme.surface
    val sheetContent = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.20f)

    fun tr(str: String): String = if (isTraditional) ChineseConverter.toTraditional(str) else str

    val filteredCandidates = remember(candidates, selectedFilterSource) {
        if (selectedFilterSource == null) candidates
        else candidates.filter { it.source == selectedFilterSource }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = sheetContent,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.5.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // 1. 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("更换歌词版本"),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = sheetContent,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = tr("正在播放: $trackTitle - $artistName"),
                        fontSize = 12.5.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (hasManualBinding) {
                    TextButton(
                        onClick = onResetToAutoMatch,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tr("恢复自动匹配"),
                            fontSize = 13.sp,
                            color = Color(0xFF1ED760),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. 搜索输入栏
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    TextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = {
                            Text(
                                text = tr("输入歌名、歌手或自定义关键词..."),
                                color = textSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            if (inputQuery.isNotBlank()) onSearch(inputQuery)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (inputQuery.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清空",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { inputQuery = "" }
                        )
                    }

                    Surface(
                        color = if (isDark) Color(0xFF272938) else Color(0xFFE2E4EB),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .bouncyClickable(
                                pressedScale = 0.94f,
                                onClick = {
                                    focusManager.clearFocus()
                                    if (inputQuery.isNotBlank()) onSearch(inputQuery)
                                }
                            )
                    ) {
                        Text(
                            text = tr("搜索"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = sheetContent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 来源过滤标签 (全部 / 网易云 / QQ 音乐)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val neteaseCount = candidates.count { it.source == LyricSource.NETEASE }
                val qqCount = candidates.count { it.source == LyricSource.QQ_MUSIC }

                FilterPill(
                    label = tr("全部") + if (candidates.isNotEmpty()) " (${candidates.size})" else "",
                    isSelected = selectedFilterSource == null,
                    isDark = isDark,
                    onClick = { selectedFilterSource = null }
                )
                FilterPill(
                    label = tr("网易云") + if (neteaseCount > 0) " ($neteaseCount)" else "",
                    isSelected = selectedFilterSource == LyricSource.NETEASE,
                    isDark = isDark,
                    onClick = { selectedFilterSource = LyricSource.NETEASE }
                )
                FilterPill(
                    label = tr("QQ 音乐") + if (qqCount > 0) " ($qqCount)" else "",
                    isSelected = selectedFilterSource == LyricSource.QQ_MUSIC,
                    isDark = isDark,
                    onClick = { selectedFilterSource = LyricSource.QQ_MUSIC }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 候选列表 / 加载状态 / 错误提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 160.dp, max = 420.dp)
            ) {
                when {
                    isSearching -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF1ED760)
                            )
                            Text(
                                text = tr("正在检索网易云与 QQ 音乐版本库..."),
                                fontSize = 13.sp,
                                color = textSecondary
                            )
                        }
                    }

                    errorMessage != null && filteredCandidates.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "🔍", fontSize = 28.sp)
                            Text(
                                text = tr(errorMessage),
                                fontSize = 14.sp,
                                color = textSecondary
                            )
                            Text(
                                text = tr("尝试修改上方关键词重新搜索"),
                                fontSize = 12.sp,
                                color = textSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }

                    filteredCandidates.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🎵", fontSize = 28.sp)
                            Text(
                                text = tr("暂无候选版本"),
                                fontSize = 13.5.sp,
                                color = textSecondary
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(filteredCandidates, key = { "${it.source}_${it.sourceId}" }) { item ->
                                CandidateItemCard(
                                    item = item,
                                    targetDurationMs = targetDurationMs,
                                    isDark = isDark,
                                    isApplying = isApplying,
                                    isTraditional = isTraditional,
                                    onClick = { onSelectCandidate(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        if (isDark) Color.White else Color(0xFF181A20)
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    }
    val contentColor = if (isSelected) {
        if (isDark) Color.Black else Color.White
    } else {
        if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.65f)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.bouncyClickable(pressedScale = 0.94f, onClick = onClick)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CandidateItemCard(
    item: LyricCandidateItem,
    targetDurationMs: Long,
    isDark: Boolean,
    isApplying: Boolean,
    isTraditional: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1C1E2A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val textPrimary = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant

    fun tr(str: String): String = if (isTraditional) ChineseConverter.toTraditional(str) else str

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 封面图片
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (item.source == LyricSource.NETEASE) "网" else "Q",
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            // 中间歌曲信息与特征标签
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // 平台来源徽章
                    val sourceBadgeBg = when (item.source) {
                        LyricSource.NETEASE -> Color(0xFFE60026).copy(alpha = 0.18f)
                        LyricSource.QQ_MUSIC -> Color(0xFF1ED760).copy(alpha = 0.18f)
                    }
                    val sourceBadgeColor = when (item.source) {
                        LyricSource.NETEASE -> if (isDark) Color(0xFFFF5252) else Color(0xFFD32F2F)
                        LyricSource.QQ_MUSIC -> if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F)
                    }
                    Surface(
                        color = sourceBadgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.source.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = sourceBadgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // 歌手与专辑
                val subInfo = buildString {
                    append(item.artist)
                    if (item.album.isNotBlank()) {
                        append(" · ")
                        append(item.album)
                    }
                }
                Text(
                    text = subInfo,
                    fontSize = 12.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 底部标签：时长判定与双语翻译
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 时长指示
                    if (item.durationMs > 0L) {
                        val totalSec = item.durationMs / 1000L
                        val durStr = String.format(java.util.Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                        val isMatched = targetDurationMs > 0L && Math.abs(targetDurationMs - item.durationMs) <= 3500L

                        Surface(
                            color = if (isMatched) Color(0xFF1ED760).copy(alpha = 0.15f) else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isMatched) tr("时长吻合 ") + durStr else durStr,
                                fontSize = 10.5.sp,
                                color = if (isMatched) Color(0xFF1ED760) else textSecondary,
                                fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // 翻译指示
                    if (item.hasTranslation) {
                        Surface(
                            color = Color(0xFF29B6F6).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tr("双语精翻"),
                                fontSize = 10.5.sp,
                                color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tr("原版字幕"),
                                fontSize = 10.5.sp,
                                color = textSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 右侧选择按钮
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "选择",
                tint = textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
