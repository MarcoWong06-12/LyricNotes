package com.linernotes.app.presentation.nowplaying.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.i18n.resolveAppMessage
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.core.util.SpotifyLauncher
import com.linernotes.app.presentation.common.*
import com.linernotes.app.presentation.floating.FloatingLyricColorPickerDialog
import com.linernotes.app.presentation.nowplaying.FuriganaDisplayMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricNotesSettingsSheet(
    trackState: TrackPlaybackState,
    hasSongStory: Boolean,
    geniusNotice: String? = null,
    isLoadingGenius: Boolean = false,
    furiganaMode: FuriganaDisplayMode,
    isTraditionalChinese: Boolean,
    themeMode: String = AiPreferences.ThemeMode.SYSTEM.code,
    appLanguage: String = AppLanguage.SYSTEM.code,
    targetLanguage: String = TranslationTargetLanguage.ZH_CN.code,
    lyricsSource: String = AiPreferences.LyricsSourcePreference.AUTO_FIRST.code,
    geniusToken: String = "",
    isAmoledMode: Boolean = false,
    lyricAlignment: Int = AiPreferences.LYRIC_ALIGN_LEFT,
    lyricTextSize: Int = AiPreferences.LYRIC_TEXT_SIZE_STANDARD,
    isDynamicAuroraEnabled: Boolean = true,
    isWordByWordEnabled: Boolean = true,
    isDeCensorEnabled: Boolean,
    showPlaybackControls: Boolean,
    songOffsetMs: Long = 0L,
    globalOffsetMs: Long = 0L,
    lyricOffsetMs: Long = 0L,
    onOpenSongStory: () -> Unit,
    onReloadGenius: () -> Unit = {},
    onAdjustSongOffset: (Long) -> Unit = {},
    onResetSongOffset: () -> Unit = {},
    onAdjustGlobalOffset: (Long) -> Unit = {},
    onResetGlobalOffset: () -> Unit = {},
    onAdjustOffset: (Long) -> Unit = onAdjustSongOffset,
    onResetOffset: () -> Unit = onResetSongOffset,
    onSetFuriganaMode: (FuriganaDisplayMode) -> Unit,
    onToggleTraditionalChinese: () -> Unit,
    onSetThemeMode: (String) -> Unit = {},
    onSetAppLanguage: (String) -> Unit = {},
    onSetTargetLanguage: (String) -> Unit = {},
    onSetLyricsSource: (String) -> Unit = {},
    onSetGeniusToken: (String) -> Unit = {},
    onSetAmoledMode: (Boolean) -> Unit = {},
    onSetLyricAlignment: (Int) -> Unit = {},
    onSetLyricTextSize: (Int) -> Unit = {},
    onSetDynamicAuroraEnabled: (Boolean) -> Unit = {},
    onSetWordByWordEnabled: (Boolean) -> Unit = {},
    onToggleDeCensor: () -> Unit,
    onTogglePlaybackControls: () -> Unit,
    onReloadLyrics: () -> Unit,
    onOpenManualSearch: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val floatingPrefs = remember { FloatingLyricsPreferences.get(context) }
    val currentTextColor by floatingPrefs.textColorFlow.collectAsState(initial = floatingPrefs.textColor)
    var showColorPickerDialog by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isPureAmoled = isDark && isAmoledMode

    // Apple Grouped Inset 配色系统
    val sheetBg = if (isDark) {
        if (isPureAmoled) Color.Black else Color(0xFF121214)
    } else Color(0xFFF2F2F7)

    val cardBg = if (isDark) {
        if (isPureAmoled) Color(0xFF0C0C0E) else Color(0xFF1C1C1E)
    } else Color.White

    val cardBorder = if (isDark) {
        if (isPureAmoled) Color(0xFF222226) else Color.White.copy(alpha = 0.08f)
    } else Color.Black.copy(alpha = 0.05f)

    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.48f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)
    val accentBlue = if (isDark) Color(0xFF64B5F6) else Color(0xFF007AFF)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        // 锁定 120Hz 高刷新率，消除弹窗滚动微卡顿
        LockWindowRefreshRateEffect()

        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部紧凑标题栏 + 关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.topBarSettings,
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier
                        .size(30.dp)
                        .bouncyIconClickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.closeBtn,
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1C1C1E).copy(alpha = 0.72f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 顶部播放曲目卡片 (Now Playing Banner)
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!trackState.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = trackState.coverUrl,
                                contentDescription = "Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(0.5.dp, cardBorder, RoundedCornerShape(10.dp))
                            )
                        } else {
                            Surface(
                                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, cardBorder),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            val displayTrackTitle = if (trackState.hasValidTrack) {
                                if (isTraditionalChinese) com.linernotes.app.core.util.ChineseConverter.toTraditional(trackState.title) else trackState.title
                            } else strings.noTrackDetected
                            val displayTrackArtist = if (trackState.hasValidTrack) {
                                if (isTraditionalChinese) com.linernotes.app.core.util.ChineseConverter.toTraditional(trackState.artist) else trackState.artist
                            } else strings.waitingForPlaybackTitle
                            Text(
                                text = displayTrackTitle,
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayTrackArtist,
                                color = textSecondary,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val sourcePillBg = when {
                            trackState.isSpotify && isDark -> Color(0xFF1DB954).copy(alpha = 0.16f)
                            trackState.isSpotify -> Color(0xFF16A34A).copy(alpha = 0.10f)
                            isDark -> Color.White.copy(alpha = 0.08f)
                            else -> Color.Black.copy(alpha = 0.05f)
                        }
                        val sourcePillBorder = when {
                            trackState.isSpotify && isDark -> Color(0xFF1ED760).copy(alpha = 0.34f)
                            trackState.isSpotify -> Color(0xFF16A34A).copy(alpha = 0.26f)
                            else -> cardBorder
                        }
                        val sourcePillFg = when {
                            trackState.isSpotify && isDark -> Color(0xFF1ED760)
                            trackState.isSpotify -> Color(0xFF128C3D)
                            else -> textPrimary
                        }
                        Surface(
                            color = sourcePillBg,
                            shape = CircleShape,
                            border = BorderStroke(0.5.dp, sourcePillBorder),
                            modifier = Modifier.bouncyClickable(pressedScale = 0.92f) {
                                SpotifyLauncher.launchSpotify(context)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (trackState.isPlaying) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = sourcePillFg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(sourcePillFg.copy(alpha = 0.85f))
                                    )
                                }
                                Text(
                                    text = trackState.sourceApp.localizedName(strings),
                                    color = sourcePillFg,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

            // 2. 外观与排版设计 (Appearance & Display)
            SettingsGroupCard(
                title = strings.displaySettingsTitle,
                isDark = isDark,
                isPureAmoled = isPureAmoled
            ) {
                // 主题模式分段选择器
                Text(
                    text = strings.themeModeLabel,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        AiPreferences.ThemeMode.SYSTEM.code to strings.themeModeSystem,
                        AiPreferences.ThemeMode.DARK.code to strings.themeModeDark,
                        AiPreferences.ThemeMode.LIGHT.code to strings.themeModeLight
                    ),
                    selectedItem = themeMode,
                    onItemSelected = onSetThemeMode,
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.amoledModeLabel,
                    subtitle = if (isDark) strings.amoledModeDesc else strings.amoledDarkOnlyHint,
                    checked = isAmoledMode,
                    enabled = isDark,
                    isDark = isDark,
                    onCheckedChange = { onSetAmoledMode(!isAmoledMode) }
                )

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.dynamicAuroraLabel,
                    subtitle = strings.dynamicAuroraDesc,
                    checked = isDynamicAuroraEnabled,
                    isDark = isDark,
                    onCheckedChange = { onSetDynamicAuroraEnabled(!isDynamicAuroraEnabled) }
                )

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.wordByWordLabel,
                    subtitle = strings.wordByWordDesc,
                    checked = isWordByWordEnabled,
                    isDark = isDark,
                    onCheckedChange = { onSetWordByWordEnabled(!isWordByWordEnabled) }
                )

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.showControlsLabel,
                    checked = showPlaybackControls,
                    isDark = isDark,
                    onCheckedChange = { onTogglePlaybackControls() }
                )

                SettingsCardDivider(dividerColor)

                // 歌词对齐
                Text(
                    text = strings.lyricAlignmentLabel,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        AiPreferences.LYRIC_ALIGN_LEFT to strings.alignLeftDefault,
                        AiPreferences.LYRIC_ALIGN_CENTER to strings.alignCenter
                    ),
                    selectedItem = lyricAlignment,
                    onItemSelected = onSetLyricAlignment,
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 歌词字号
                Text(
                    text = strings.lyricTextSizeLabel,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        AiPreferences.LYRIC_TEXT_SIZE_COMPACT to strings.textSizeCompact,
                        AiPreferences.LYRIC_TEXT_SIZE_STANDARD to strings.textSizeStandard,
                        AiPreferences.LYRIC_TEXT_SIZE_LARGE to strings.textSizeLarge,
                        AiPreferences.LYRIC_TEXT_SIZE_EXTRA_LARGE to strings.textSizeExtraLarge
                    ),
                    selectedItem = lyricTextSize,
                    onItemSelected = onSetLyricTextSize,
                    isDark = isDark
                )
            }

            // 3. 语言与歌词内容 (Language & Lyrics)
            SettingsGroupCard(
                title = strings.appLanguageLabel,
                isDark = isDark,
                isPureAmoled = isPureAmoled
            ) {
                val systemLanguageLabel = when (strings.themeModeSystem) {
                    "System Default" -> "Auto"
                    "システム設定" -> "自動"
                    else -> strings.themeModeSystem
                }
                // 介面语言分段选择器
                AppleSegmentedControl(
                    items = listOf(
                        AppLanguage.SYSTEM.code to systemLanguageLabel,
                        AppLanguage.ZH_CN.code to "简体中文",
                        AppLanguage.ZH_TW.code to "繁體中文",
                        AppLanguage.EN.code to "English",
                        AppLanguage.JA.code to "日本語"
                    ),
                    selectedItem = appLanguage,
                    onItemSelected = onSetAppLanguage,
                    isDark = isDark,
                    fontSize = 11.sp
                )

                SettingsCardDivider(dividerColor)

                // 歌词翻译目标语言
                Text(
                    text = strings.targetLanguageLabel,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TranslationTargetLanguage.entries.forEach { lang ->
                        SettingsOptionChip(
                            label = lang.displayName,
                            selected = targetLanguage.equals(lang.code, ignoreCase = true),
                            isDark = isDark,
                            isPureAmoled = isPureAmoled,
                            onClick = { onSetTargetLanguage(lang.code) }
                        )
                    }
                }

                SettingsCardDivider(dividerColor)

                // 歌词与翻译首选源
                Text(
                    text = strings.lyricsSourceSetting,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiPreferences.LyricsSourcePreference.entries.forEach { source ->
                        val sourceLabel = when (source) {
                            AiPreferences.LyricsSourcePreference.AUTO_FIRST -> strings.sourceAuto
                            AiPreferences.LyricsSourcePreference.NETEASE_ONLY -> strings.sourceOfficial
                            AiPreferences.LyricsSourcePreference.QQ_ONLY -> strings.sourceQq
                            AiPreferences.LyricsSourcePreference.KUGOU_ONLY -> strings.sourceKugou
                            AiPreferences.LyricsSourcePreference.MUSIXMATCH_ONLY -> strings.sourceMusixmatch
                            AiPreferences.LyricsSourcePreference.LRCLIB_ONLY -> strings.sourceLrclib
                        }
                        SettingsOptionChip(
                            label = sourceLabel,
                            selected = lyricsSource.equals(source.code, ignoreCase = true),
                            isDark = isDark,
                            isPureAmoled = isPureAmoled,
                            onClick = { onSetLyricsSource(source.code) }
                        )
                    }
                }

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.traditionalChineseLabel,
                    checked = isTraditionalChinese,
                    isDark = isDark,
                    onCheckedChange = { onToggleTraditionalChinese() }
                )

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.deCensorLabel,
                    checked = isDeCensorEnabled,
                    isDark = isDark,
                    onCheckedChange = { onToggleDeCensor() }
                )

                SettingsCardDivider(dividerColor)

                // 日语假名注音
                Text(
                    text = strings.furiganaTitle,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        FuriganaDisplayMode.OFF to strings.furiganaOff,
                        FuriganaDisplayMode.HIRAGANA to strings.furiganaHiragana,
                        FuriganaDisplayMode.ROMAJI to strings.furiganaRomaji
                    ),
                    selectedItem = furiganaMode,
                    onItemSelected = onSetFuriganaMode,
                    isDark = isDark
                )
            }

            // 4. 时间轴同步延迟补偿 (Timeline Sync Offset)
            var offsetScope by remember { mutableStateOf(0) } // 0 = 单曲, 1 = 全局
            SettingsGroupCard(
                title = strings.timelineOffsetLabel,
                isDark = isDark,
                isPureAmoled = isPureAmoled
            ) {
                // 顶栏：单曲与全局全宽分段切换，避免英文/日文标签截断
                Text(
                    text = if (offsetScope == 0) strings.offsetScopeSongTitle else strings.offsetScopeGlobalTitle,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(0 to strings.offsetScopeSong, 1 to strings.offsetScopeGlobal),
                    selectedItem = offsetScope,
                    onItemSelected = { offsetScope = it },
                    isDark = isDark,
                    height = 32.dp
                )

                val currentVal = if (offsetScope == 0) songOffsetMs else globalOffsetMs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (offsetScope == 0) strings.offsetScopeSongDesc else strings.offsetScopeGlobalDesc,
                        color = textSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when (currentVal) {
                            0L -> "0 ms"
                            else -> "${if (currentVal > 0) "+" else ""}${currentVal} ms"
                        },
                        color = if (currentVal == 0L) Color(0xFF34C759) else accentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }

                if (offsetScope == 0 && globalOffsetMs != 0L) {
                    Text(
                        text = "${strings.offsetEffectiveLabel}: ${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs} ms (${strings.offsetScopeGlobal}: ${if (globalOffsetMs > 0) "+" else ""}${globalOffsetMs} ms)",
                        color = textSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val onAdjust = if (offsetScope == 0) onAdjustSongOffset else onAdjustGlobalOffset
                    val onReset = if (offsetScope == 0) onResetSongOffset else onResetGlobalOffset

                    OffsetButton("-100ms", onClick = { onAdjust(-100) }, modifier = Modifier.weight(1f), isDark = isDark)
                    OffsetButton("-50ms", onClick = { onAdjust(-50) }, modifier = Modifier.weight(1f), isDark = isDark)
                    OffsetButton("0ms", onClick = onReset, modifier = Modifier.weight(1.1f), isReset = true, isDark = isDark)
                    OffsetButton("+50ms", onClick = { onAdjust(50) }, modifier = Modifier.weight(1f), isDark = isDark)
                    OffsetButton("+100ms", onClick = { onAdjust(100) }, modifier = Modifier.weight(1f), isDark = isDark)
                }
            }

            // 5. 桌面悬浮歌词设置 (Desktop Floating Lyrics)
            val isFloatingActive by FloatingLyricsService.isRunningFlow.collectAsState()
            val launchFloatingLyrics = rememberFloatingLyricsLauncher()
            var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isOverlayGranted = Settings.canDrawOverlays(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            val currentBgAlpha by floatingPrefs.backgroundAlphaFlow.collectAsState(initial = floatingPrefs.backgroundAlpha)
            val isLocked by floatingPrefs.isLockedFlow.collectAsState(initial = floatingPrefs.isLocked)
            val currentDisplayMode by floatingPrefs.displayModeFlow.collectAsState(initial = floatingPrefs.displayMode)
            val currentCapsuleWidth by floatingPrefs.capsuleWidthDpFlow.collectAsState(initial = floatingPrefs.capsuleWidthDp)
            val currentFloatingStyle by floatingPrefs.floatingStyleFlow.collectAsState(initial = floatingPrefs.floatingStyle)
            val hideInForeground by floatingPrefs.hideWhenAppInForegroundFlow.collectAsState(initial = floatingPrefs.hideWhenAppInForeground)
            val currentFontScale by floatingPrefs.fontScaleFlow.collectAsState(initial = floatingPrefs.fontScale)
            val currentTextAlignment by floatingPrefs.textAlignmentFlow.collectAsState(initial = floatingPrefs.textAlignment)

            SettingsGroupCard(
                title = strings.floatingLyricsTitle,
                isDark = isDark,
                isPureAmoled = isPureAmoled
            ) {
                val onToggleFloating: (Boolean) -> Unit = { checked ->
                    if (checked) {
                        isOverlayGranted = Settings.canDrawOverlays(context)
                        launchFloatingLyrics()
                    } else {
                        FloatingLyricsService.stop(context)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .bouncyItemClickable(pressedScale = 0.985f) {
                            onToggleFloating(!isFloatingActive)
                        }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = strings.floatingLyricsTitle,
                            color = textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        if (!isOverlayGranted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.floatingPermissionDesc,
                                color = if (isDark) Color(0xFFFFB74D) else Color(0xFFD97706),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    AppleToggleSwitch(
                        checked = isFloatingActive,
                        enabled = true,
                        isDark = isDark,
                        onCheckedChange = onToggleFloating
                    )
                }

                SettingsCardDivider(dividerColor)

                // 悬浮形态
                Text(
                    text = strings.floatingStyleLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        FloatingLyricsPreferences.STYLE_PURE_LYRICS to strings.floatingPureLyrics,
                        FloatingLyricsPreferences.STYLE_CAPSULE_CARD to strings.floatingCapsuleCard
                    ),
                    selectedItem = currentFloatingStyle,
                    onItemSelected = { floatingPrefs.floatingStyle = it },
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 背景不透明度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.floatingBgAlphaLabel,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "${(currentBgAlpha * 100).toInt()}%",
                        color = accentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
                AppleSegmentedControl(
                    items = listOf(
                        0.0f to "0%",
                        0.30f to "30%",
                        0.60f to "60%",
                        0.85f to "85%",
                        1.0f to "100%"
                    ),
                    selectedItem = currentBgAlpha,
                    onItemSelected = { floatingPrefs.backgroundAlpha = it },
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 排版方式
                Text(
                    text = strings.floatingWrapModeLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        FloatingLyricsPreferences.DISPLAY_MODE_WRAP to strings.floatingModeWrap,
                        FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE to strings.floatingModeMarquee
                    ),
                    selectedItem = currentDisplayMode,
                    onItemSelected = { floatingPrefs.displayMode = it },
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 文字对齐
                Text(
                    text = strings.floatingTextAlignLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                AppleSegmentedControl(
                    items = listOf(
                        FloatingLyricsPreferences.ALIGNMENT_CENTER to strings.alignCenter,
                        FloatingLyricsPreferences.ALIGNMENT_LEFT to strings.alignLeft
                    ),
                    selectedItem = currentTextAlignment,
                    onItemSelected = { floatingPrefs.textAlignment = it },
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 悬浮宽度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.floatingWidthLabel,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "${currentCapsuleWidth} dp",
                        color = accentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
                AppleSegmentedControl(
                    items = listOf(
                        320 to "320 dp",
                        356 to "356 dp",
                        376 to "376 dp"
                    ),
                    selectedItem = currentCapsuleWidth,
                    onItemSelected = { floatingPrefs.capsuleWidthDp = it },
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 字体缩放
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.floatingFontScaleLabel,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "${kotlin.math.round(currentFontScale * 100f).toInt()}%",
                        color = accentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
                AppleSegmentedControl(
                    items = listOf(
                        0.85f to "85%",
                        1.00f to "100%",
                        1.15f to "115%",
                        1.30f to "130%",
                        1.45f to "145%"
                    ),
                    selectedItem = currentFontScale,
                    onItemSelected = { floatingPrefs.fontScale = it },
                    isDark = isDark
                )

                SettingsCardDivider(dividerColor)

                // 文字颜色选择与自定义拾色器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.floatingTextColorLabel,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )

                    // 十六进制颜色代码微胶囊，轻点直接呼出自定义拾色器
                    val effectiveHex = remember(currentTextColor) {
                        val argb = if (currentTextColor != -1) currentTextColor else 0xFFFFFFFF.toInt()
                        "#" + String.format("%06X", 0xFFFFFF and argb)
                    }
                    Surface(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                        modifier = Modifier.bouncyClickable(pressedScale = 0.92f) {
                            showColorPickerDialog = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (currentTextColor != -1) Color(currentTextColor) else Color.White)
                            )
                            Text(
                                text = effectiveHex,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                color = textPrimary
                            )
                            Text(
                                text = strings.floatingCustomColorAction,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFFD97706)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colorPalette = listOf(
                        0xFFFFFFFF.toInt(), // 纯白
                        0xFFFFD54F.toInt(), // 暖金
                        0xFF1ED760.toInt(), // 荧光绿 (Spotify)
                        0xFF00E5FF.toInt(), // 电光青
                        0xFF81D4FA.toInt(), // 天空蓝
                        0xFFF48FB1.toInt()  // 樱花粉
                    )
                    colorPalette.forEach { colorVal ->
                        val isSelected = currentTextColor == colorVal
                        val colorCompose = Color(colorVal)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colorCompose)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.5.dp,
                                    color = if (isSelected) (if (isDark) Color.White else Color.Black) else Color.Black.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .bouncyClickable(pressedScale = 0.88f) {
                                    floatingPrefs.textColor = colorVal
                                }
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (colorCompose.luminance() > 0.45f) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 第 7 项：自定义颜色按钮 (Custom Swatch)
                    val isCustomActive = currentTextColor != -1 && !colorPalette.contains(currentTextColor)
                    val rainbowBorder = Brush.sweepGradient(
                        listOf(
                            Color(0xFFFF0055),
                            Color(0xFFFFCC00),
                            Color(0xFF33FF00),
                            Color(0xFF00EEFF),
                            Color(0xFF0066FF),
                            Color(0xFFFF00CC),
                            Color(0xFFFF0055)
                        )
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isCustomActive) Color(currentTextColor) else (if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)))
                            .border(
                                width = if (isCustomActive) 2.5.dp else 1.5.dp,
                                brush = rainbowBorder,
                                shape = CircleShape
                            )
                            .bouncyClickable(pressedScale = 0.88f) {
                                showColorPickerDialog = true
                            }
                    ) {
                        if (isCustomActive) {
                            val customCompose = Color(currentTextColor)
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Custom Color Selected",
                                tint = if (customCompose.luminance() > 0.45f) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = strings.floatingCustomColorTitle,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.70f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.floatingLockTouch,
                    checked = isLocked,
                    isDark = isDark,
                    onCheckedChange = { floatingPrefs.isLocked = !isLocked }
                )

                SettingsCardDivider(dividerColor)

                SettingsSwitchRow(
                    title = strings.floatingHideInForeground,
                    checked = hideInForeground,
                    isDark = isDark,
                    onCheckedChange = { floatingPrefs.hideWhenAppInForeground = !hideInForeground }
                )
            }

            // 6. Genius 歌曲创作背景与典故 (Genius Insights)
            if (hasSongStory) {
                val storyCardBg = if (isDark) Color(0xFF221E14) else Color(0xFFFFFBF0)
                val storyBorder = if (isDark) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color(0xFFFFB300).copy(alpha = 0.35f)
                val storyColor = if (isDark) Color(0xFFFFE082) else Color(0xFFD97706)

                Surface(
                    color = storyCardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, storyBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyItemClickable {
                            onDismiss()
                            onOpenSongStory()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💡", fontSize = 18.sp, fontFamily = FontFamily.SansSerif)
                            Text(
                                text = strings.songStoryTitle,
                                color = storyColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = storyColor
                        )
                    }
                }
            } else {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isLoadingGenius) "⏳" else "💡",
                                fontSize = 18.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Column {
                                Text(
                                    text = strings.songStoryAndTrivia,
                                    color = textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = if (isLoadingGenius) {
                                        strings.searchingGeniusStatus
                                    } else {
                                        geniusNotice?.let { resolveAppMessage(it, strings) } ?: strings.geniusNoticeNotFound
                                    },
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, cardBorder),
                            modifier = Modifier.bouncyClickable(
                                enabled = !isLoadingGenius,
                                pressedScale = 0.92f,
                                onClick = onReloadGenius
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isLoadingGenius) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = strings.searchingStatus,
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = textPrimary, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = strings.retryBtn,
                                        color = textPrimary,
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

            // Genius Developer Token（始终可见，避免限流时无处配置）
            SettingsGroupCard(
                title = strings.geniusTokenLabel,
                isDark = isDark,
                isPureAmoled = isPureAmoled
            ) {
                var tokenDraft by remember(geniusToken) { mutableStateOf(geniusToken) }
                var tokenVisible by remember { mutableStateOf(false) }
                val focusManager = LocalFocusManager.current
                val hasUnsavedToken = tokenDraft.trim() != geniusToken.trim()

                Text(
                    text = strings.geniusTokenDesc,
                    color = textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        placeholder = {
                            Text(
                                text = strings.geniusTokenPlaceholder,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = Color(0xFF0A84FF),
                            unfocusedBorderColor = cardBorder,
                            focusedPlaceholderColor = textSecondary,
                            unfocusedPlaceholderColor = textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        color = if (hasUnsavedToken) Color(0xFF34C759) else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp),
                        border = if (hasUnsavedToken) null else BorderStroke(0.5.dp, cardBorder),
                        modifier = Modifier
                            .height(50.dp)
                            .bouncyClickable(pressedScale = 0.94f) {
                                onSetGeniusToken(tokenDraft)
                                focusManager.clearFocus()
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = strings.saveTokenBtn,
                                color = if (hasUnsavedToken) Color.White else textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            // 7. 快捷操作栏 (Quick Actions)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 搜索更换版本
                Surface(
                    color = if (isDark) Color(0xFF1E2028) else Color(0xFFE8EBF2),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .bouncyClickable(pressedScale = 0.96f) {
                            onDismiss()
                            onOpenManualSearch()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = textPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.searchLyricVersionBtn,
                            fontSize = 13.5.sp,
                            color = textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.5.dp, cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .bouncyClickable(pressedScale = 0.95f) {
                                onDismiss()
                                onReloadLyrics()
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = textPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.rematchLyricsBtn,
                                fontSize = 13.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF1DB954),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .bouncyClickable(pressedScale = 0.95f) {
                                SpotifyLauncher.launchSpotify(context)
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = strings.openMusicAppBtn,
                                fontSize = 13.5.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showColorPickerDialog) {
        FloatingLyricColorPickerDialog(
            currentColor = currentTextColor,
            isDark = isDark,
            isPureAmoled = isPureAmoled,
            isTraditional = isTraditionalChinese,
            onDismiss = { showColorPickerDialog = false },
            onColorSelected = { selectedColor ->
                floatingPrefs.textColor = selectedColor
                showColorPickerDialog = false
            }
        )
    }
}

/**
 * 具有 Apple 质感的高级分组卡片 (Grouped Inset Card)
 */
@Composable
private fun SettingsGroupCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    isPureAmoled: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardBg = if (isDark) {
        if (isPureAmoled) Color(0xFF0C0C0E) else Color(0xFF1C1C1E)
    } else Color.White

    val cardBorder = if (isDark) {
        if (isPureAmoled) Color(0xFF222226) else Color.White.copy(alpha = 0.08f)
    } else Color.Black.copy(alpha = 0.05f)

    val titleColor = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.48f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
            )
        }
        Surface(
            color = cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 16.dp 水平内边距同时使组内分割线保持 16.dp 左右缩进
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsCardDivider(color: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 1.dp),
        color = color,
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsOptionChip(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    isPureAmoled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> Color(0xFF0A84FF).copy(alpha = if (isDark) 0.24f else 0.14f)
        isDark -> if (isPureAmoled) Color(0xFF141416) else Color.White.copy(alpha = 0.06f)
        else -> Color.Black.copy(alpha = 0.05f)
    }
    val border = when {
        selected -> Color(0xFF0A84FF).copy(alpha = 0.55f)
        isDark -> Color.White.copy(alpha = 0.10f)
        else -> Color.Black.copy(alpha = 0.06f)
    }
    val textColor = when {
        selected -> if (isDark) Color(0xFF64B5FF) else Color(0xFF0066CC)
        isDark -> Color.White.copy(alpha = 0.80f)
        else -> Color(0xFF1C1C1E).copy(alpha = 0.85f)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, border),
        modifier = Modifier.bouncyClickable(pressedScale = 0.95f, onClick = onClick)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OffsetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isReset: Boolean = false,
    isDark: Boolean = true
) {
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val resetBg = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.09f)
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val resetBorder = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)

    val buttonScale by animateFloatAsState(
        targetValue = if (isReset) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        label = "offsetBtnScale"
    )

    Surface(
        color = if (isReset) resetBg else unselectedBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, if (isReset) resetBorder else unselectedBorder),
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .bouncyClickable(
                pressedScale = 0.90f,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
            Text(
                text = text,
                color = if (isReset) textColor else textColor.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = if (isReset) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    isDark: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.48f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .bouncyItemClickable(enabled = enabled, pressedScale = 0.985f) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .alpha(if (enabled) 1f else 0.45f)
        ) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.5.dp))
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        AppleToggleSwitch(
            checked = checked,
            enabled = enabled,
            isDark = isDark,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AppleToggleSwitch(
    checked: Boolean,
    enabled: Boolean,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumbSize = 24.dp
    val thumbInset = 3.dp
    val maxTravelPx = with(density) { (trackWidth - thumbSize - thumbInset * 2).toPx() }

    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 580f),
        label = "appleSwitchThumb"
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> Color(0xFF34C759)
            isDark -> Color(0xFF39393D)
            else -> Color(0xFFE9E9EA)
        },
        animationSpec = spring(stiffness = 620f),
        label = "appleSwitchTrack"
    )

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { newValue ->
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {
                    }
                    onCheckedChange(newValue)
                }
            )
            .padding(thumbInset),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = thumbProgress * maxTravelPx
                }
                .size(thumbSize)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, Color.Black.copy(alpha = 0.06f), CircleShape)
        )
    }
}
