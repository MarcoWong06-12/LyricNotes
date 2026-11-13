package com.linernotes.app.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.lyric.BilingualSentenceAligner
import com.linernotes.app.core.util.ChineseConverter

enum class BilingualDisplayTab {
    PARALLEL,
    CHINESE,
    ORIGINAL
}

data class AlignedParagraph(
    val original: String,
    val translation: String?
)

object ParagraphAligner {
    private val PARAGRAPH_SPLIT_REGEX = Regex("""\n\s*\n""")

    fun align(original: String, translation: String?): List<AlignedParagraph> {
        val origParagraphs = original.split(PARAGRAPH_SPLIT_REGEX)
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

    val isTranslationAlsoChinese = remember(effectiveTranslation) {
        !effectiveTranslation.isNullOrBlank() && AiAnnotationCurator.isAlreadyChinese(effectiveTranslation)
    }

    val isSingleLanguageChinese = isAlreadyChinese && (effectiveTranslation.isNullOrBlank() || isTranslationAlsoChinese)

    val hasTranslation = !effectiveTranslation.isNullOrBlank() && !isSingleLanguageChinese &&
        effectiveTranslation.trim() != originalText.trim() &&
        effectiveTranslation.any { it.isLetterOrDigit() }

    var selectedTab by remember(originalText, hasTranslation) {
        mutableStateOf(if (hasTranslation) BilingualDisplayTab.PARALLEL else BilingualDisplayTab.ORIGINAL)
    }

    val alignedParagraphs = remember(originalText, effectiveTranslation) {
        BilingualSentenceAligner.align(originalText, effectiveTranslation)
    }

    val isEffectiveDark = isDark || MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val primaryTextColor = if (isEffectiveDark) Color.White.copy(alpha = 0.92f) else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isEffectiveDark) Color.White.copy(alpha = 0.66f) else Color(0xFF1C1C1E).copy(alpha = 0.68f)
    val quoteBg = if (isEffectiveDark) Color.White.copy(alpha = 0.055f) else Color.White
    val quoteBorder = if (isEffectiveDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)
    val quoteAccentBar = if (isEffectiveDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.16f)
    val quoteEyebrowColor = if (isEffectiveDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.42f)

    val strings = LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 顶部模式切换分段控制器 (AppleSegmentedControl 共享弹簧滑块)
        if (!isSingleLanguageChinese && hasTranslation) {
            AppleSegmentedControl(
                items = listOf(
                    BilingualDisplayTab.PARALLEL to strings.bilingualTabParallel,
                    BilingualDisplayTab.CHINESE to strings.bilingualTabTranslated,
                    BilingualDisplayTab.ORIGINAL to strings.bilingualTabOriginal
                ),
                selectedItem = selectedTab,
                onItemSelected = { selectedTab = it },
                isDark = isEffectiveDark,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // 正在自动翻译中的轻量状态提示
        AnimatedVisibility(visible = isTranslating) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isEffectiveDark) Color(0xFFFFD54F).copy(alpha = 0.12f) else Color(0xFFFFF8E1),
                border = BorderStroke(0.5.dp, if (isEffectiveDark) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color(0xFFFFB300).copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.6.dp,
                        color = if (isEffectiveDark) Color(0xFFFFD54F) else Color(0xFFD97706)
                    )
                    Text(
                        text = strings.bilingualTranslatingNotice,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (isEffectiveDark) Color(0xFFFFD54F) else Color(0xFFD97706),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // 若未具备中文翻译且未在翻译中，显示一键即时生成按钮
        if (!isSingleLanguageChinese && !hasTranslation && !isTranslating && onRetryTranslation != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isEffectiveDark) Color.White.copy(alpha = 0.08f) else Color.White,
                border = BorderStroke(0.5.dp, if (isEffectiveDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                shadowElevation = if (isEffectiveDark) 0.dp else 0.5.dp,
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
                    Text("✨", fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.bilingualGenerateAction,
                        color = primaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        // 原文是纯中文且无非中文译文
        if (isSingleLanguageChinese) {
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
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.5.dp, quoteBorder),
                                    shadowElevation = if (isEffectiveDark) 0.dp else 0.5.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(quoteAccentBar)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "ORIGINAL",
                                                fontSize = 10.sp,
                                                lineHeight = 12.sp,
                                                letterSpacing = 0.9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = quoteEyebrowColor,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                            Text(
                                                text = enPara,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                letterSpacing = 0.1.sp,
                                                color = secondaryTextColor,
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
                            lineHeight = 24.5.sp,
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
