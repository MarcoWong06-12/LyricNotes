package com.linernotes.app.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.lyric.BilingualSentenceAligner
import com.linernotes.app.core.util.ChineseConverter

enum class BilingualDisplayTab {
    PARALLEL, // 中英对照
    CHINESE,  // 纯中文
    ORIGINAL  // 纯英文
}

data class AlignedParagraph(
    val original: String,
    val translation: String?
)

object ParagraphAligner {
    fun align(original: String, translation: String?): List<AlignedParagraph> {
        val origParagraphs = original.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (origParagraphs.isEmpty()) {
            return if (original.isNotBlank()) listOf(AlignedParagraph(original.trim(), translation?.trim())) else emptyList()
        }

        if (translation.isNullOrBlank()) {
            return origParagraphs.map { AlignedParagraph(it, null) }
        }

        val transParagraphs = translation.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (origParagraphs.size == transParagraphs.size) {
            return origParagraphs.zip(transParagraphs) { o, t -> AlignedParagraph(o, t) }
        }

        if (origParagraphs.size == 1) {
            return listOf(AlignedParagraph(origParagraphs[0], translation.trim()))
        }

        val altTrans = translation.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (origParagraphs.size == altTrans.size) {
            return origParagraphs.zip(altTrans) { o, t -> AlignedParagraph(o, t) }
        }

        return origParagraphs.mapIndexed { idx, o ->
            AlignedParagraph(o, transParagraphs.getOrNull(idx))
        }
    }
}

/**
 * 专业期刊级段落双语对照视图
 * 支持 [ 中英对照 | 纯中文 | 纯英文 ] 平滑切换，遵循 Apple Editorial 刊物排版规范。
 */
@Composable
fun BilingualContentView(
    originalText: String,
    translatedText: String?,
    isTranslating: Boolean = false,
    isTraditional: Boolean = false,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    onRetryTranslation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (originalText.isBlank()) return

    val isAlreadyChinese = remember(originalText) {
        AiAnnotationCurator.isAlreadyChinese(originalText)
    }

    val effectiveTranslation = remember(translatedText, isTraditional) {
        if (translatedText.isNullOrBlank()) null
        else if (isTraditional) ChineseConverter.toTraditional(translatedText)
        else translatedText
    }

    val hasTranslation = !effectiveTranslation.isNullOrBlank() && (isAlreadyChinese || Regex("""[\u4e00-\u9fa5]""").containsMatchIn(effectiveTranslation))

    var selectedTab by remember(originalText, hasTranslation) {
        mutableStateOf(if (hasTranslation) BilingualDisplayTab.PARALLEL else BilingualDisplayTab.ORIGINAL)
    }

    val alignedParagraphs = remember(originalText, effectiveTranslation) {
        BilingualSentenceAligner.align(originalText, effectiveTranslation)
    }

    val isEffectiveDark = isDark || MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val primaryTextColor = if (isEffectiveDark) Color.White.copy(alpha = 0.95f) else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isEffectiveDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f)
    val quoteBg = if (isEffectiveDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)
    val quoteBorder = if (isEffectiveDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部模式切换分段控制器 (Apple Segmented Style，告别原先突兀的紫色高亮)
        if (!isAlreadyChinese && hasTranslation) {
            val trackBg = if (isEffectiveDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            Surface(
                color = trackBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        BilingualDisplayTab.PARALLEL to (if (isTraditional) "中英對照" else "中英对照"),
                        BilingualDisplayTab.CHINESE to (if (isTraditional) "純中文" else "纯中文"),
                        BilingualDisplayTab.ORIGINAL to (if (isTraditional) "純英文" else "纯英文")
                    )
                    tabs.forEach { (tabVal, label) ->
                        val isSelected = selectedTab == tabVal
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                if (isEffectiveDark) Color(0xFF38383A) else Color.White
                            } else Color.Transparent,
                            animationSpec = spring(stiffness = 650f),
                            label = "bilingualPillBg"
                        )
                        val pillBorder by animateColorAsState(
                            targetValue = if (isSelected) {
                                if (isEffectiveDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.04f)
                            } else Color.Transparent,
                            animationSpec = spring(stiffness = 650f),
                            label = "bilingualPillBorder"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) primaryTextColor else secondaryTextColor,
                            animationSpec = spring(stiffness = 650f),
                            label = "bilingualText"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillBg)
                                .border(0.5.dp, pillBorder, RoundedCornerShape(8.dp))
                                .bouncyClickable(pressedScale = 0.95f) {
                                    selectedTab = tabVal
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }

        // 正在自动翻译中的轻量状态提示
        AnimatedVisibility(visible = isTranslating) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isEffectiveDark) Color(0xFFFFD54F).copy(alpha = 0.12f) else Color(0xFFFFF8E1),
                border = BorderStroke(0.5.dp, if (isEffectiveDark) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color(0xFFFFB300).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.6.dp,
                        color = if (isEffectiveDark) Color(0xFFFFD54F) else Color(0xFFD97706)
                    )
                    Text(
                        text = if (isTraditional) "正在為您生成精準中文對照釋義..." else "正在为您生成精准中文对照释义...",
                        fontSize = 12.sp,
                        color = if (isEffectiveDark) Color(0xFFFFD54F) else Color(0xFFD97706),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 若未具备中文翻译且未在翻译中，显示一键即时生成按钮
        if (!isAlreadyChinese && !hasTranslation && !isTranslating && onRetryTranslation != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isEffectiveDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE8EBF2),
                border = BorderStroke(0.5.dp, if (isEffectiveDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(
                        pressedScale = 0.96f,
                        onClick = onRetryTranslation
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("✨", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTraditional) "即刻生成中文對照釋義" else "即刻生成中文对照释义",
                        color = primaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 原文是纯中文
        if (isAlreadyChinese) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                alignedParagraphs.forEach { p ->
                    val text = p.units.joinToString(" ") { it.original }
                    val displayText = if (isTraditional) ChineseConverter.toTraditional(text) else text
                    Text(
                        text = displayText,
                        fontSize = 15.sp,
                        lineHeight = 25.sp,
                        letterSpacing = 0.2.sp,
                        color = primaryTextColor,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            return@Column
        }

        // 按照当前模式展示
        when (selectedTab) {
            BilingualDisplayTab.PARALLEL -> {
                // 1. 期刊级段落双语对照：中文自然段落主阅读 + 英文原文段落精致卡片收纳
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    alignedParagraphs.forEach { para ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 中文译文（完整自然段落，行文流畅）
                            val zhPara = para.units.map { it.chinese.ifBlank { it.original } }.joinToString(" ")
                            val displayText = if (isTraditional) ChineseConverter.toTraditional(zhPara) else zhPara
                            if (displayText.isNotBlank()) {
                                Text(
                                    text = displayText,
                                    fontSize = 15.sp,
                                    lineHeight = 25.sp,
                                    letterSpacing = 0.2.sp,
                                    color = primaryTextColor,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }

                            // 英文原文（优雅文献级底板引用，去除单句交替的碎片感）
                            val enPara = para.units.map { it.original }.joinToString(" ")
                            if (enPara.isNotBlank() && enPara != displayText) {
                                Surface(
                                    color = quoteBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.5.dp, quoteBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = enPara,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.5.sp,
                                        color = secondaryTextColor,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BilingualDisplayTab.CHINESE -> {
                // 2. 纯中文流畅阅读视图
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    alignedParagraphs.forEach { para ->
                        val zhPara = para.units.map { it.chinese.ifBlank { it.original } }.joinToString(" ")
                        val text = if (isTraditional) ChineseConverter.toTraditional(zhPara) else zhPara
                        Text(
                            text = text,
                            fontSize = 15.sp,
                            lineHeight = 25.sp,
                            letterSpacing = 0.2.sp,
                            color = primaryTextColor,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            BilingualDisplayTab.ORIGINAL -> {
                // 3. 纯英文原文视图
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    alignedParagraphs.forEach { para ->
                        val enPara = para.units.map { it.original }.joinToString(" ")
                        Text(
                            text = enPara,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.15.sp,
                            color = primaryTextColor,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}
