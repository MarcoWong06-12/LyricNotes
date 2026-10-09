package com.linernotes.app.presentation.nowplaying.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.linernotes.app.core.util.SpotifyLauncher
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
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.TrackPlaybackState
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
    themeMode: String = com.linernotes.app.core.preference.AiPreferences.ThemeMode.SYSTEM.code,
    appLanguage: String = com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code,
    isAmoledMode: Boolean = false,
    lyricAlignment: Int = com.linernotes.app.core.preference.AiPreferences.LYRIC_ALIGN_LEFT,
    isDeCensorEnabled: Boolean,
    showPlaybackControls: Boolean,
    lyricOffsetMs: Long,
    onOpenSongStory: () -> Unit,
    onReloadGenius: () -> Unit = {},
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSetFuriganaMode: (FuriganaDisplayMode) -> Unit,
    onToggleTraditionalChinese: () -> Unit,
    onSetThemeMode: (String) -> Unit = {},
    onSetAppLanguage: (String) -> Unit = {},
    onSetAmoledMode: (Boolean) -> Unit = {},
    onSetLyricAlignment: (Int) -> Unit = {},
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
    val sheetBg = if (isDark) {
        if (isPureAmoled) Color.Black else Color(0xFF13141B)
    } else MaterialTheme.colorScheme.surface
    val sheetContent = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val cardBg = if (isDark) {
        if (isPureAmoled) Color(0xFF0C0C0E) else Color.White.copy(alpha = 0.05f)
    } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val cardBorder = if (isDark) {
        if (isPureAmoled) Color(0xFF222226) else Color.White.copy(alpha = 0.10f)
    } else Color.Black.copy(alpha = 0.08f)
    val textPrimary = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val handleColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.20f)

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部曲目与播放状态卡片
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
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (trackState.hasValidTrack) trackState.title else strings.noTrackDetected,
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (trackState.hasValidTrack) trackState.artist else strings.waitingForPlaybackTitle,
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (trackState.isSpotify) Color(0xFF1DB954).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        shape = CircleShape,
                        border = BorderStroke(0.5.dp, if (trackState.isSpotify) Color(0xFF1ED760).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.bouncyClickable(pressedScale = 0.92f) {
                            SpotifyLauncher.launchSpotify(context)
                        }
                    ) {
                        Text(
                            text = trackState.sourceApp.displayName,
                            color = if (trackState.isSpotify) Color(0xFF1ED760) else (if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 外观主题选择卡片
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.themeModeLabel,
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SegmentPill(
                            title = strings.themeModeSystem,
                            isSelected = themeMode == com.linernotes.app.core.preference.AiPreferences.ThemeMode.SYSTEM.code,
                            onClick = { onSetThemeMode(com.linernotes.app.core.preference.AiPreferences.ThemeMode.SYSTEM.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        SegmentPill(
                            title = strings.themeModeDark,
                            isSelected = themeMode == com.linernotes.app.core.preference.AiPreferences.ThemeMode.DARK.code,
                            onClick = { onSetThemeMode(com.linernotes.app.core.preference.AiPreferences.ThemeMode.DARK.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        SegmentPill(
                            title = strings.themeModeLight,
                            isSelected = themeMode == com.linernotes.app.core.preference.AiPreferences.ThemeMode.LIGHT.code,
                            onClick = { onSetThemeMode(com.linernotes.app.core.preference.AiPreferences.ThemeMode.LIGHT.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    SettingsSwitchRow(
                        title = strings.amoledModeLabel,
                        subtitle = strings.amoledModeDesc,
                        checked = isAmoledMode,
                        isDark = isDark,
                        onCheckedChange = { onSetAmoledMode(!isAmoledMode) }
                    )
                }
            }

            // 界面语言选择卡片
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.appLanguageLabel,
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SegmentPill(
                            title = strings.themeModeSystem,
                            isSelected = appLanguage == AppLanguage.SYSTEM.code,
                            onClick = { onSetAppLanguage(AppLanguage.SYSTEM.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        SegmentPill(
                            title = "简体中文",
                            isSelected = appLanguage == AppLanguage.ZH_CN.code,
                            onClick = { onSetAppLanguage(AppLanguage.ZH_CN.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SegmentPill(
                            title = "繁體中文",
                            isSelected = appLanguage == AppLanguage.ZH_TW.code,
                            onClick = { onSetAppLanguage(AppLanguage.ZH_TW.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        SegmentPill(
                            title = "English",
                            isSelected = appLanguage == AppLanguage.EN.code,
                            onClick = { onSetAppLanguage(AppLanguage.EN.code) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Genius 歌曲创作背景与典故状态
            if (hasSongStory) {
                val storyCardBg = if (isDark) Color(0xFFFFD54F).copy(alpha = 0.12f) else Color(0xFFFFF8E1).copy(alpha = 0.95f)
                val storyBorder = if (isDark) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color(0xFFFFB300).copy(alpha = 0.50f)
                val storyColor = if (isDark) Color(0xFFFFE082) else Color(0xFFE65100)
                Surface(
                    color = storyCardBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, storyBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyItemClickable {
                            onDismiss()
                            onOpenSongStory()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💡", fontSize = 20.sp)
                            Text(
                                text = strings.songStoryTitle,
                                color = storyColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
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
                val infiniteTransition = rememberInfiniteTransition(label = "geniusPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.12f,
                    targetValue = if (isLoadingGenius) 0.55f else 0.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "geniusBorderPulse"
                )

                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        0.5.dp,
                        if (isLoadingGenius) Color(0xFF1ED760).copy(alpha = pulseAlpha) else cardBorder
                    ),
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isLoadingGenius) "正在重新检索 Genius 典故与背景故事..." else (geniusNotice ?: "未检索到本曲 Genius 典故注释"),
                                    color = if (isLoadingGenius) Color(0xFF1ED760) else textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // 重试 / 检索中 胶囊按钮 (专为手指触控优化，高响应物理弹簧反馈)
                        Surface(
                            color = if (isLoadingGenius) Color(0xFF1ED760).copy(alpha = 0.12f) else Color(0xFF1ED760).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                0.5.dp,
                                if (isLoadingGenius) Color(0xFF1ED760).copy(alpha = 0.30f) else Color(0xFF1ED760).copy(alpha = 0.40f)
                            ),
                            modifier = Modifier
                                .bouncyClickable(
                                    enabled = !isLoadingGenius,
                                    pressedScale = 0.90f,
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
                                        color = Color(0xFF1ED760)
                                    )
                                    Text(
                                        text = strings.searchingStatus,
                                        color = Color(0xFF1ED760),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF1ED760),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = strings.retryBtn,
                                        color = Color(0xFF1ED760),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 歌词时间轴微调与低延迟补偿 (Lyrics Offset)
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.timelineOffsetLabel,
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = when (lyricOffsetMs) {
                                0L -> "0 ms"
                                else -> "${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs} ms"
                            },
                            color = if (lyricOffsetMs == -200L) Color(0xFF1ED760) else textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OffsetButton("-100ms", onClick = { onAdjustOffset(-100) }, modifier = Modifier.weight(1f), isDark = isDark)
                        OffsetButton("-50ms", onClick = { onAdjustOffset(-50) }, modifier = Modifier.weight(1f), isDark = isDark)
                        OffsetButton("-200ms", onClick = onResetOffset, modifier = Modifier.weight(1.1f), isReset = true, isDark = isDark)
                        OffsetButton("+50ms", onClick = { onAdjustOffset(50) }, modifier = Modifier.weight(1f), isDark = isDark)
                        OffsetButton("+100ms", onClick = { onAdjustOffset(100) }, modifier = Modifier.weight(1f), isDark = isDark)
                    }
                }
            }

            // 4. 桌面悬浮歌词
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

            val floatingCardBg = if (isFloatingActive) {
                if (isDark) Color(0xFF162538).copy(alpha = 0.70f) else Color(0xFFE1F5FE).copy(alpha = 0.85f)
            } else cardBg

            val floatingCardBorder = if (isFloatingActive) {
                if (isDark) Color(0xFF81D4FA).copy(alpha = 0.50f) else Color(0xFF0288D1).copy(alpha = 0.40f)
            } else cardBorder

            Surface(
                color = floatingCardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, floatingCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🫧", fontSize = 19.sp)
                            Column {
                                Text(
                                    text = strings.floatingLyricsTitle,
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                if (!isOverlayGranted) {
                                    Text(
                                        text = strings.floatingPermissionDesc,
                                        color = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
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
                                checkedTrackColor = Color(0xFF03A9F4),
                                uncheckedThumbColor = if (isDark) Color.LightGray else Color.DarkGray,
                                uncheckedTrackColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                            )
                        )
                    }

                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // 4.0 悬浮歌词形态
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.floatingStyleLabel,
                            color = textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val styleOptions = listOf(
                                FloatingLyricsPreferences.STYLE_PURE_LYRICS to strings.floatingPureLyrics,
                                FloatingLyricsPreferences.STYLE_CAPSULE_CARD to strings.floatingCapsuleCard
                            )
                            styleOptions.forEach { (styleVal, label) ->
                                FluidOptionPill(
                                    label = label,
                                    isSelected = currentFloatingStyle == styleVal,
                                    isDark = isDark,
                                    onClick = { floatingPrefs.floatingStyle = styleVal },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.1 背景不透明度
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.floatingBgAlphaLabel,
                                color = textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "${(currentBgAlpha * 100).toInt()}%",
                                color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val alphaOptions = listOf(
                                0.00f to "0%",
                                0.30f to "30%",
                                0.60f to "60%",
                                0.85f to "85%",
                                1.00f to "100%"
                            )
                            alphaOptions.forEach { (alphaVal, label) ->
                                FluidOptionPill(
                                    label = label,
                                    isSelected = kotlin.math.abs(currentBgAlpha - alphaVal) < 0.08f,
                                    isDark = isDark,
                                    onClick = { floatingPrefs.backgroundAlpha = alphaVal },
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.2 长歌词排版
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.floatingWrapModeLabel,
                            color = textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modeOptions = listOf(
                                FloatingLyricsPreferences.DISPLAY_MODE_WRAP to strings.floatingModeWrap,
                                FloatingLyricsPreferences.DISPLAY_MODE_MARQUEE to strings.floatingModeMarquee
                            )
                            modeOptions.forEach { (modeVal, label) ->
                                FluidOptionPill(
                                    label = label,
                                    isSelected = currentDisplayMode == modeVal,
                                    isDark = isDark,
                                    onClick = { floatingPrefs.displayMode = modeVal },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.3 悬浮文字对齐
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.floatingTextAlignLabel,
                            color = textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val alignOptions = listOf(
                                FloatingLyricsPreferences.ALIGNMENT_CENTER to strings.alignCenter,
                                FloatingLyricsPreferences.ALIGNMENT_LEFT to strings.alignLeft
                            )
                            alignOptions.forEach { (alignVal, label) ->
                                FluidOptionPill(
                                    label = label,
                                    isSelected = currentTextAlignment == alignVal,
                                    isDark = isDark,
                                    onClick = { floatingPrefs.textAlignment = alignVal },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.3 悬浮窗宽度
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.floatingWidthLabel,
                                color = textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "${currentCapsuleWidth} dp",
                                color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val widthOptions = listOf(
                                320 to "320 dp",
                                356 to "356 dp",
                                376 to "376 dp"
                            )
                            widthOptions.forEach { (widthVal, label) ->
                                FluidOptionPill(
                                    label = label,
                                    isSelected = currentCapsuleWidth == widthVal,
                                    isDark = isDark,
                                    onClick = { floatingPrefs.capsuleWidthDp = widthVal },
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.4 歌词字体大小
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.floatingFontScaleLabel,
                                color = textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "${(currentFontScale * 100).toInt()}%",
                                color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fontOptions = listOf(
                                0.85f to "85%",
                                1.00f to "100%",
                                1.15f to "115%",
                                1.30f to "130%",
                                1.45f to "145%"
                            )
                            fontOptions.forEach { (scaleVal, label) ->
                                FluidOptionPill(
                                    label = label,
                                    isSelected = kotlin.math.abs(currentFontScale - scaleVal) < 0.06f,
                                    isDark = isDark,
                                    onClick = { floatingPrefs.fontScale = scaleVal },
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.5 歌词文字颜色
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.floatingTextColorLabel,
                            color = textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        val colorPresets = remember {
                            listOf(
                                -1 to Color.White,                       // 纯白
                                0xFFFFD54F.toInt() to Color(0xFFFFD54F), // 暖金
                                0xFF1ED760.toInt() to Color(0xFF1ED760), // 荧光绿
                                0xFF00E5FF.toInt() to Color(0xFF00E5FF), // 电光青
                                0xFFFF80AB.toInt() to Color(0xFFFF80AB), // 樱花粉
                                0xFFB388FF.toInt() to Color(0xFFB388FF), // 浅紫
                                0xFFFF9100.toInt() to Color(0xFFFF9100), // 暖橙
                                0xFF121212.toInt() to Color(0xFF121212)  // 曜黑
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorPresets.forEach { (colorVal, colorCompose) ->
                                val isSelected = currentTextColor == colorVal
                                val isDarkSwatch = colorCompose.luminance() < 0.20f
                                val swatchBorder = if (isSelected) {
                                    if (isDark) Color(0xFF00E5FF) else Color(0xFF0288D1)
                                } else if (isDarkSwatch) {
                                    if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.35f)
                                } else {
                                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colorCompose)
                                        .border(
                                            width = if (isSelected) 2.5.dp else if (isDarkSwatch) 1.dp else 0.5.dp,
                                            color = swatchBorder,
                                            shape = CircleShape
                                        )
                                        .bouncyClickable(pressedScale = 0.85f) {
                                            floatingPrefs.textColor = colorVal
                                        }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (colorCompose.luminance() > 0.45f) Color.Black else Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4.6 锁定触摸穿透
                    SettingsSwitchRow(
                        title = strings.floatingLockTouch,
                        checked = isLocked,
                        isDark = isDark,
                        onCheckedChange = {
                            floatingPrefs.isLocked = !isLocked
                        }
                    )

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    // 4.7 应用在前台时隐藏悬浮窗
                    SettingsSwitchRow(
                        title = strings.floatingHideInForeground,
                        checked = hideInForeground,
                        isDark = isDark,
                        onCheckedChange = {
                            floatingPrefs.hideWhenAppInForeground = !hideInForeground
                        }
                    )
                }
            }

            // 5. 显示与排版设置
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingsSwitchRow(
                        title = strings.showControlsLabel,
                        checked = showPlaybackControls,
                        isDark = isDark,
                        onCheckedChange = { onTogglePlaybackControls() }
                    )

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    SettingsSwitchRow(
                        title = strings.deCensorLabel,
                        checked = isDeCensorEnabled,
                        isDark = isDark,
                        onCheckedChange = { onToggleDeCensor() }
                    )

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    SettingsSwitchRow(
                        title = strings.traditionalChineseLabel,
                        checked = isTraditionalChinese,
                        isDark = isDark,
                        onCheckedChange = { onToggleTraditionalChinese() }
                    )

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.lyricAlignmentLabel,
                            color = textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FluidOptionPill(
                                label = strings.alignLeftDefault,
                                isSelected = lyricAlignment == com.linernotes.app.core.preference.AiPreferences.LYRIC_ALIGN_LEFT,
                                isDark = isDark,
                                onClick = { onSetLyricAlignment(com.linernotes.app.core.preference.AiPreferences.LYRIC_ALIGN_LEFT) },
                                modifier = Modifier.weight(1f)
                            )
                            FluidOptionPill(
                                label = strings.alignCenter,
                                isSelected = lyricAlignment == com.linernotes.app.core.preference.AiPreferences.LYRIC_ALIGN_CENTER,
                                isDark = isDark,
                                onClick = { onSetLyricAlignment(com.linernotes.app.core.preference.AiPreferences.LYRIC_ALIGN_CENTER) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 6. 日语假名注音
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.furiganaTitle,
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FuriganaPill(
                            title = strings.furiganaOff,
                            isSelected = furiganaMode == FuriganaDisplayMode.OFF,
                            isDark = isDark,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.OFF) },
                            modifier = Modifier.weight(1f)
                        )
                        FuriganaPill(
                            title = strings.furiganaHiragana,
                            isSelected = furiganaMode == FuriganaDisplayMode.HIRAGANA,
                            isDark = isDark,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.HIRAGANA) },
                            modifier = Modifier.weight(1f)
                        )
                        FuriganaPill(
                            title = strings.furiganaRomaji,
                            isSelected = furiganaMode == FuriganaDisplayMode.ROMAJI,
                            isDark = isDark,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.ROMAJI) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. 搜索与更换歌词版本入口
            Surface(
                color = if (isDark) Color(0xFF1E2235) else Color(0xFFE8EBF5),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF333852) else Color(0xFFD0D5E5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .bouncyClickable(
                        pressedScale = 0.96f,
                        onClick = {
                            onDismiss()
                            onOpenManualSearch()
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.searchLyricVersionBtn,
                        fontSize = 14.sp,
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 7. 快捷操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val reloadBg = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                val reloadBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

                Surface(
                    color = reloadBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, reloadBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .bouncyClickable(
                            pressedScale = 0.95f,
                            onClick = {
                                onDismiss()
                                onReloadLyrics()
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = textPrimary, modifier = Modifier.size(16.dp))
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
                        .bouncyClickable(
                            pressedScale = 0.95f,
                            onClick = {
                                SpotifyLauncher.launchSpotify(context)
                            }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(strings.openMusicAppBtn, fontSize = 13.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
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
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val resetBg = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val resetBorder = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f)

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
            .height(38.dp)
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
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1DB954),
                uncheckedThumbColor = if (isDark) Color.LightGray else Color.DarkGray,
                uncheckedTrackColor = uncheckedTrack
            )
        )
    }
}

@Composable
private fun SegmentPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val unselectedText = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1ED760) else unselectedBg,
        animationSpec = spring(stiffness = 600f),
        label = "segmentBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else unselectedText,
        animationSpec = spring(stiffness = 600f),
        label = "segmentText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1ED760) else unselectedBorder,
        animationSpec = spring(stiffness = 600f),
        label = "segmentBorder"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isSelected) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        label = "segmentScale"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, borderColor),
        modifier = modifier
            .height(38.dp)
            .graphicsLayer {
                scaleX = pillScale
                scaleY = pillScale
            }
            .bouncyClickable(
                pressedScale = 0.92f,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = textColor,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun FuriganaPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val unselectedText = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1ED760) else unselectedBg,
        animationSpec = spring(stiffness = 600f),
        label = "furiganaBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else unselectedText,
        animationSpec = spring(stiffness = 600f),
        label = "furiganaText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF1ED760) else unselectedBorder,
        animationSpec = spring(stiffness = 600f),
        label = "furiganaBorder"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isSelected) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        label = "furiganaScale"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, borderColor),
        modifier = modifier
            .height(38.dp)
            .graphicsLayer {
                scaleX = pillScale
                scaleY = pillScale
            }
            .bouncyClickable(
                pressedScale = 0.92f,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * 具有物理弹性动效的高级流体选项胶囊 (用于浮动设置排版、宽度、透明度等分段控制)
 */
@Composable
private fun FluidOptionPill(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    activeColor: Color = if (isDark) Color(0xFF03A9F4).copy(alpha = 0.30f) else Color(0xFF0288D1),
    activeBorderColor: Color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1),
    activeTextColor: Color = if (isDark) Color(0xFF81D4FA) else Color.White,
    fontSize: TextUnit = 12.sp
) {
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else unselectedBg,
        animationSpec = spring(stiffness = 650f),
        label = "optBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) activeTextColor else textSecondary,
        animationSpec = spring(stiffness = 650f),
        label = "optText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) activeBorderColor else unselectedBorder,
        animationSpec = spring(stiffness = 650f),
        label = "optBorder"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isSelected) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
        label = "optScale"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, borderColor),
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = pillScale
                scaleY = pillScale
            }
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = textColor,
                fontSize = fontSize,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
