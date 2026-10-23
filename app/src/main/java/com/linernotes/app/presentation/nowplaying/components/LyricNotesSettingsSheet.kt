package com.linernotes.app.presentation.nowplaying.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.core.util.SpotifyLauncher
import com.linernotes.app.presentation.common.*
import com.linernotes.app.presentation.nowplaying.FuriganaDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
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
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = handleColor,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.5.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部播放曲目卡片 (Now Playing Banner)
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!trackState.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = trackState.coverUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(10.dp),
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
                        Text(
                            text = if (trackState.hasValidTrack) trackState.title else strings.noTrackDetected,
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (trackState.hasValidTrack) trackState.artist else strings.waitingForPlaybackTitle,
                            color = textSecondary,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (trackState.isSpotify) Color(0xFF1DB954).copy(alpha = 0.12f) else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                        shape = CircleShape,
                        border = BorderStroke(0.5.dp, if (trackState.isSpotify) Color(0xFF1ED760).copy(alpha = 0.30f) else cardBorder),
                        modifier = Modifier.bouncyClickable(pressedScale = 0.92f) {
                            SpotifyLauncher.launchSpotify(context)
                        }
                    ) {
                        Text(
                            text = trackState.sourceApp.displayName,
                            color = if (trackState.isSpotify) Color(0xFF1DB954) else textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
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
                    fontWeight = FontWeight.SemiBold
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.amoledModeLabel,
                    subtitle = strings.amoledModeDesc,
                    checked = isAmoledMode,
                    isDark = isDark,
                    onCheckedChange = { onSetAmoledMode(!isAmoledMode) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.dynamicAuroraLabel,
                    subtitle = strings.dynamicAuroraDesc,
                    checked = isDynamicAuroraEnabled,
                    isDark = isDark,
                    onCheckedChange = { onSetDynamicAuroraEnabled(!isDynamicAuroraEnabled) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.wordByWordLabel,
                    subtitle = strings.wordByWordDesc,
                    checked = isWordByWordEnabled,
                    isDark = isDark,
                    onCheckedChange = { onSetWordByWordEnabled(!isWordByWordEnabled) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.showControlsLabel,
                    checked = showPlaybackControls,
                    isDark = isDark,
                    onCheckedChange = { onTogglePlaybackControls() }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 歌词对齐
                Text(
                    text = strings.lyricAlignmentLabel,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 歌词字号
                Text(
                    text = strings.lyricTextSizeLabel,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
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
                // 介面语言分段选择器
                AppleSegmentedControl(
                    items = listOf(
                        AppLanguage.SYSTEM.code to strings.themeModeSystem,
                        AppLanguage.ZH_CN.code to "简体中文",
                        AppLanguage.ZH_TW.code to "繁體中文",
                        AppLanguage.EN.code to "English"
                    ),
                    selectedItem = appLanguage,
                    onItemSelected = onSetAppLanguage,
                    isDark = isDark
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.traditionalChineseLabel,
                    checked = isTraditionalChinese,
                    isDark = isDark,
                    onCheckedChange = { onToggleTraditionalChinese() }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.deCensorLabel,
                    checked = isDeCensorEnabled,
                    isDark = isDark,
                    onCheckedChange = { onToggleDeCensor() }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 日语假名注音
                Text(
                    text = strings.furiganaTitle,
                    color = textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
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
                // 顶栏：单曲与全局分段切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (offsetScope == 0) strings.offsetScopeSongTitle else strings.offsetScopeGlobalTitle,
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box(modifier = Modifier.width(136.dp)) {
                        AppleSegmentedControl(
                            items = listOf(0 to strings.offsetScopeSong, 1 to strings.offsetScopeGlobal),
                            selectedItem = offsetScope,
                            onItemSelected = { offsetScope = it },
                            isDark = isDark,
                            height = 30.dp
                        )
                    }
                }

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
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when (currentVal) {
                            0L -> "0 ms"
                            else -> "${if (currentVal > 0) "+" else ""}${currentVal} ms"
                        },
                        color = if (currentVal == 0L) Color(0xFF34C759) else (if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (offsetScope == 0 && globalOffsetMs != 0L) {
                    Text(
                        text = "${strings.offsetEffectiveLabel}: ${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs} ms (${strings.offsetScopeGlobal}: ${if (globalOffsetMs > 0) "+" else ""}${globalOffsetMs} ms)",
                        color = textSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp
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
            val isOverlayGranted = Settings.canDrawOverlays(context)
            val floatingPrefs = remember { FloatingLyricsPreferences.get(context) }
            val currentBgAlpha by floatingPrefs.backgroundAlphaFlow.collectAsState(initial = floatingPrefs.backgroundAlpha)
            val currentTextColor by floatingPrefs.textColorFlow.collectAsState(initial = floatingPrefs.textColor)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.floatingLyricsTitle,
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isOverlayGranted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.floatingPermissionDesc,
                                color = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Switch(
                        checked = isFloatingActive,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (Settings.canDrawOverlays(context)) {
                                    FloatingLyricsService.start(context)
                                } else {
                                    FloatingLyricsService.requestOverlayPermission(context)
                                }
                            } else {
                                FloatingLyricsService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = if (isDark) Color.LightGray else Color.DarkGray,
                            uncheckedTrackColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                        )
                    )
                }

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 悬浮形态
                Text(
                    text = strings.floatingStyleLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

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
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(currentBgAlpha * 100).toInt()}%",
                        color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 排版方式
                Text(
                    text = strings.floatingWrapModeLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 文字对齐
                Text(
                    text = strings.floatingTextAlignLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

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
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${currentCapsuleWidth} dp",
                        color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

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
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(currentFontScale * 100).toInt()}%",
                        color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                // 文字颜色选择
                Text(
                    text = strings.floatingTextColorLabel,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val colorPalette = listOf(
                        0xFFFFFFFF.toInt(),
                        0xFFE0E0E0.toInt(),
                        0xFFB0BEC5.toInt(),
                        0xFFFFD54F.toInt(),
                        0xFF81D4FA.toInt(),
                        0xFFA5D6A7.toInt(),
                        0xFFF48FB1.toInt()
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
                }

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = strings.floatingLockTouch,
                    checked = isLocked,
                    isDark = isDark,
                    onCheckedChange = { floatingPrefs.isLocked = !isLocked }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

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
                            Text("💡", fontSize = 18.sp)
                            Text(
                                text = strings.songStoryTitle,
                                color = storyColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
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
                            Text(if (isLoadingGenius) "⏳" else "💡", fontSize = 18.sp)
                            Column {
                                Text(
                                    text = strings.songStoryAndTrivia,
                                    color = textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isLoadingGenius) "正在检索 Genius 典故..." else (geniusNotice ?: "未收录本曲 Genius 典故"),
                                    color = textSecondary,
                                    fontSize = 12.sp
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
                                    Text(text = strings.searchingStatus, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = textPrimary, modifier = Modifier.size(13.dp))
                                    Text(text = strings.retryBtn, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
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
                            fontWeight = FontWeight.SemiBold
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
                            Text(strings.rematchLyricsBtn, fontSize = 13.sp, color = textPrimary, fontWeight = FontWeight.Medium)
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
                            Text(strings.openMusicAppBtn, fontSize = 13.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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

    val titleColor = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
        Surface(
            color = cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 原生 iOS / macOS 质感的微动效分段选择器 (AppleSegmentedControl)
 */
@Composable
private fun <T> AppleSegmentedControl(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    height: Dp = 34.dp
) {
    val trackBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val textPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f)

    Surface(
        color = trackBg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (itemVal, label) ->
                val isSelected = selectedItem == itemVal

                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) Color(0xFF38383A) else Color.White
                    } else Color.Transparent,
                    animationSpec = spring(stiffness = 650f),
                    label = "segPillBg"
                )

                val pillBorder by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.04f)
                    } else Color.Transparent,
                    animationSpec = spring(stiffness = 650f),
                    label = "segPillBorder"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) textPrimary else textSecondary,
                    animationSpec = spring(stiffness = 650f),
                    label = "segText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .border(0.5.dp, pillBorder, RoundedCornerShape(8.dp))
                        .bouncyClickable(pressedScale = 0.95f) {
                            onItemSelected(itemVal)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
            val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            Text(
                text = text,
                color = if (isReset) textColor else textColor.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = if (isReset) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    isDark: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val textPrimary = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
    val uncheckedTrack = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bouncyItemClickable(pressedScale = 0.985f) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759),
                uncheckedThumbColor = if (isDark) Color.LightGray else Color.DarkGray,
                uncheckedTrackColor = uncheckedTrack
            )
        )
    }
}
