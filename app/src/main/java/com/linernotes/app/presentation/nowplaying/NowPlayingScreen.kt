package com.linernotes.app.presentation.nowplaying

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.linernotes.app.core.floating.FloatingLyricsService
import com.linernotes.app.core.util.SpotifyLauncher
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.playback.MediaPlaybackSyncService
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.presentation.booklet.components.AmbientGlowBackground
import com.linernotes.app.presentation.common.*
import com.linernotes.app.presentation.nowplaying.components.LyricNotesSettingsSheet
import com.linernotes.app.presentation.nowplaying.components.NowPlayingGeniusSheet
import com.linernotes.app.presentation.nowplaying.components.NowPlayingQueueSheet
import com.linernotes.app.presentation.nowplaying.components.NowPlayingSongStorySheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val nowData = state.nowPlayingData
    val trackState = nowData.playbackState
    val context = LocalContext.current

    val hasTrack = trackState.hasValidTrack
    val isGranted = remember { mutableStateOf(MediaPlaybackSyncService.isNotificationAccessGranted(context)) }

    // 检查通知监听权限
    LaunchedEffect(Unit) {
        isGranted.value = MediaPlaybackSyncService.isNotificationAccessGranted(context)
    }

    // 固化事件闭包引用，防止因匿名函数对象变动引起子组件无谓重组
    val onLineClicked: (BilingualLyricLine) -> Unit = remember(viewModel) {
        { line: BilingualLyricLine ->
            val pos = line.startTimeMs
            if (pos != null) {
                viewModel.seekTo(pos)
            }
        }
    }
    val onAnnotationClicked: (LyricAnnotationEntity) -> Unit = remember(viewModel) {
        { annotation: LyricAnnotationEntity -> viewModel.openGeniusAnnotation(annotation) }
    }
    val onRetryLyrics: () -> Unit = remember(viewModel) { { viewModel.reloadLyrics() } }
    val onOpenManualSearch: () -> Unit = remember(viewModel) { { viewModel.openManualSearchSheet() } }
    val onOpenSongStory: () -> Unit = remember(viewModel) { { viewModel.openSongStory() } }
    val onOpenQueue: () -> Unit = remember(viewModel) { { viewModel.openQueueSheet() } }
    val onOpenSettings: () -> Unit = remember(viewModel) { { viewModel.openSettings() } }
    val onPlayPause: () -> Unit = remember(viewModel) { { viewModel.togglePlayPause() } }
    val onNext: () -> Unit = remember(viewModel) { { viewModel.skipToNext() } }
    val onPrevious: () -> Unit = remember(viewModel) { { viewModel.skipToPrevious() } }
    val onToggleShuffle: () -> Unit = remember(viewModel) { { viewModel.toggleShuffle() } }
    val onCycleRepeat: () -> Unit = remember(viewModel) { { viewModel.cycleRepeatMode() } }
    val onSeekTo: (Long) -> Unit = remember(viewModel) { { posMs: Long -> viewModel.seekTo(posMs) } }
    val onCloseQueue: () -> Unit = remember(viewModel) { { viewModel.closeQueueSheet() } }
    val onSkipToQueueItem: (QueueTrackItem) -> Unit = remember(viewModel) {
        { item: QueueTrackItem -> viewModel.skipToQueueItem(item) }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    if (state.isAmoledMode) Color.Black else Color(0xFF090A0E)
                } else MaterialTheme.colorScheme.background
            )
    ) {
        // 1. 灵动流体弥散背景 (纯净温润呼吸，AMOLED 模式下纯黑 0-nits 彻底休眠像素)
        AmbientGlowBackground(
            coverUrl = trackState.coverUrl,
            isDark = isDark,
            isAmoledMode = state.isAmoledMode,
            isDynamicAurora = state.isDynamicAuroraEnabled,
            modifier = Modifier.fillMaxSize()
        )

        // 2. 核心主内容区 (歌词全屏贯通，并在呼出底部抽屉时呈现 iOS / Apple Music 规范的流体视差微下沉)
        val isAnySheetOpen = state.isSettingsSheetOpen || state.isGeniusSheetOpen || state.isSongStorySheetOpen || state.isQueueSheetOpen || state.isManualSearchSheetOpen
        val contentParallaxScale by animateFloatAsState(
            targetValue = when {
                state.isQueueSheetOpen -> 0.935f
                isAnySheetOpen -> 0.955f
                else -> 1.0f
            },
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
            label = "sheetParallaxScale"
        )
        val contentParallaxAlpha by animateFloatAsState(
            targetValue = when {
                state.isQueueSheetOpen -> 0.68f
                isAnySheetOpen -> 0.84f
                else -> 1.0f
            },
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
            label = "sheetParallaxAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentParallaxScale
                    scaleY = contentParallaxScale
                    alpha = contentParallaxAlpha
                    transformOrigin = TransformOrigin(0.5f, 0.42f)
                }
        ) {
            if (hasTrack) {
                NowPlayingLyricsContent(
                    lyrics = nowData.lyrics,
                    currentLineIndex = nowData.currentLineIndex,
                    annotatedLines = nowData.annotatedLines,
                    isPlaying = trackState.isPlaying,
                    isTraditional = state.isTraditionalChinese,
                    isLoadingLyrics = nowData.isLoadingLyrics,
                    isDark = isDark,
                    isAmoledMode = state.isAmoledMode,
                    trackState = trackState,
                    lyricOffsetMs = state.lyricOffsetMs,
                    isWordByWordEnabled = state.isWordByWordEnabled,
                    lyricAlignment = state.lyricAlignment,
                    showPlaybackControls = state.showPlaybackControls,
                    onLineClicked = onLineClicked,
                    onAnnotationClicked = onAnnotationClicked,
                    onRetryLyrics = onRetryLyrics,
                    onOpenManualSearch = onOpenManualSearch
                )
            } else {
                NowPlayingIdleContent(
                    isDark = isDark,
                    isNotificationGranted = isGranted.value,
                    onGrantPermission = {
                        MediaPlaybackSyncService.openNotificationAccessSettings(context)
                        isGranted.value = MediaPlaybackSyncService.isNotificationAccessGranted(context)
                    },
                    onLaunchSpotify = {
                        SpotifyLauncher.launchSpotify(context)
                    }
                )
            }

            // 3. 极简顶部状态微栏 (为歌词留出 80% 屏幕纵向空间，集成外层歌曲故事直达入口)
            NowPlayingTopBar(
                trackState = trackState,
                hasSongStory = nowData.songStory != null,
                isTraditional = state.isTraditionalChinese,
                isDark = isDark,
                onOpenSongStory = onOpenSongStory,
                onOpenQueue = onOpenQueue,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 4. Genius 获取失败/状态提醒微浮标 (iOS Dynamic Island 悬浮微胶囊风格)
            AnimatedVisibility(
                visible = (nowData.geniusNoticeMessage != null || nowData.isLoadingGenius) && hasTrack,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { -it / 2 } + scaleIn(initialScale = 0.94f),
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 2 } + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = if (isDark) {
                        if (state.isAmoledMode) Color(0xFF0C0C0E) else Color(0xFF1E202B).copy(alpha = 0.94f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) {
                            if (state.isAmoledMode) Color(0xFF222226) else Color.White.copy(alpha = 0.12f)
                        } else {
                            Color.Black.copy(alpha = 0.08f)
                        }
                    ),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (nowData.isLoadingGenius) "⏳" else "💡", fontSize = 12.sp)
                        val noticeMsg = if (nowData.isLoadingGenius) {
                            strings.searchingGeniusStatus
                        } else {
                            val raw = nowData.geniusNoticeMessage ?: ""
                            if (state.isTraditionalChinese) ChineseConverter.toTraditional(raw) else raw
                        }
                        Text(
                            text = noticeMsg,
                            color = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        if (nowData.isLoadingGenius) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                val geniusAccent = if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F)
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = geniusAccent
                                )
                                Text(
                                    text = strings.searchingStatus,
                                    color = geniusAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        } else {
                            val geniusAccent = if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F)
                            Text(
                                text = strings.retryBtn,
                                color = geniusAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .bouncyClickable(pressedScale = 0.92f) { viewModel.retryGenius() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.closeBtn,
                            tint = if (isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .bouncyIconClickable { viewModel.dismissGeniusNotice() }
                        )
                    }
                }
            }

            // 5. 方案 1：底栏悬浮毛玻璃全能胶囊 (物理弹簧与呼吸式扩展，集合歌曲信息、滑条与播控)
            AnimatedVisibility(
                visible = hasTrack && state.showPlaybackControls,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.88f, stiffness = 320f)) { it / 3 } + scaleIn(initialScale = 0.95f),
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 3 } + scaleOut(targetScale = 0.95f),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NowPlayingFloatingGlassPlayer(
                    trackState = trackState,
                    isTraditional = state.isTraditionalChinese,
                    isDark = isDark,
                    isAmoledMode = state.isAmoledMode,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onSeekTo = onSeekTo,
                    onOpenQueue = onOpenQueue,
                    onOpenSettings = onOpenSettings
                )
            }
        }

        // 待播队列 (Queue) 底部抽屉 (仿 Spotify 队列界面)
        if (state.isQueueSheetOpen) {
            NowPlayingQueueSheet(
                trackState = trackState,
                isAmoledMode = state.isAmoledMode,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onPlayPause = onPlayPause,
                onSkipToQueueItem = onSkipToQueueItem,
                onDismiss = onCloseQueue
            )
        }

        // 统一设置抽屉 (包含时间轴微调、繁简、假名、外观主题、语言、控制栏开关等)
        if (state.isSettingsSheetOpen) {
            LyricNotesSettingsSheet(
                trackState = trackState,
                hasSongStory = nowData.songStory != null,
                geniusNotice = nowData.geniusNoticeMessage,
                isLoadingGenius = nowData.isLoadingGenius,
                furiganaMode = state.furiganaMode,
                isTraditionalChinese = state.isTraditionalChinese,
                themeMode = state.themeMode,
                appLanguage = state.appLanguage,
                isAmoledMode = state.isAmoledMode,
                lyricAlignment = state.lyricAlignment,
                isDynamicAuroraEnabled = state.isDynamicAuroraEnabled,
                isWordByWordEnabled = state.isWordByWordEnabled,
                isDeCensorEnabled = state.isDeCensorEnabled,
                showPlaybackControls = state.showPlaybackControls,
                lyricOffsetMs = state.lyricOffsetMs,
                onOpenSongStory = { viewModel.openSongStory() },
                onReloadGenius = { viewModel.retryGenius() },
                onAdjustOffset = { delta -> viewModel.adjustLyricOffset(delta) },
                onResetOffset = { viewModel.resetLyricOffset() },
                onSetFuriganaMode = { mode -> viewModel.setFuriganaMode(mode) },
                onToggleTraditionalChinese = { viewModel.toggleTraditionalChinese() },
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onSetAppLanguage = { viewModel.setAppLanguage(it) },
                onSetAmoledMode = { viewModel.setAmoledMode(it) },
                onSetLyricAlignment = { viewModel.setLyricAlignment(it) },
                onSetDynamicAuroraEnabled = { viewModel.setDynamicAuroraEnabled(it) },
                onSetWordByWordEnabled = { viewModel.setWordByWordEnabled(it) },
                onToggleDeCensor = { viewModel.toggleDeCensor() },
                onTogglePlaybackControls = { viewModel.togglePlaybackControls() },
                onReloadLyrics = { viewModel.reloadLyrics() },
                onOpenManualSearch = onOpenManualSearch,
                onDismiss = { viewModel.closeSettings() }
            )
        }

        // 手动搜索与更换歌词版本抽屉
        if (state.isManualSearchSheetOpen) {
            com.linernotes.app.presentation.nowplaying.components.ManualLyricSearchSheet(
                trackTitle = trackState.title,
                artistName = trackState.artist,
                targetDurationMs = trackState.durationMs,
                candidates = state.manualCandidates,
                isSearching = state.isSearchingCandidates,
                isApplying = state.isApplyingCandidate,
                hasManualBinding = state.hasManualBinding,
                errorMessage = state.candidateSearchError,
                isTraditional = state.isTraditionalChinese,
                isAmoledMode = state.isAmoledMode,
                onSearch = { query -> viewModel.searchCandidates(query) },
                onSelectCandidate = { candidate -> viewModel.selectCandidate(candidate) },
                onResetToAutoMatch = { viewModel.resetToAutoMatch() },
                onDismiss = { viewModel.closeManualSearchSheet() }
            )
        }

        // Genius Behind The Lyrics 底部抽屉
        if (state.isGeniusSheetOpen) {
            val matchedLyricTranslation = remember(state.selectedAnnotation, nowData.lyrics) {
                val annot = state.selectedAnnotation ?: return@remember null
                if (!annot.lyricTranslation.isNullOrBlank()) {
                    return@remember annot.lyricTranslation
                }
                val frag = annot.lyricFragment.trim().lowercase()
                if (frag.isNotBlank()) {
                    val matched = nowData.lyrics.filter { line ->
                        val orig = line.original.trim().lowercase()
                        orig.isNotBlank() && (frag.contains(orig) || orig.contains(frag)) && line.translation.isNotBlank()
                    }
                    if (matched.isNotEmpty()) {
                        matched.joinToString("\n") { it.translation }
                    } else null
                } else null
            }

            NowPlayingGeniusSheet(
                annotation = state.selectedAnnotation,
                lyricTranslation = matchedLyricTranslation,
                isTranslating = state.isAnnotationTranslating,
                isTraditional = state.isTraditionalChinese,
                isAmoledMode = state.isAmoledMode,
                onRetryTranslation = { viewModel.retryAnnotationTranslation() },
                onDismiss = { viewModel.closeGeniusSheet() }
            )
        }

        // 歌曲背景故事抽屉
        if (state.isSongStorySheetOpen) {
            NowPlayingSongStorySheet(
                story = nowData.songStory,
                isTranslating = state.isSongStoryTranslating,
                isTraditional = state.isTraditionalChinese,
                isAmoledMode = state.isAmoledMode,
                onRetryTranslation = { viewModel.retrySongStoryTranslation() },
                onDismiss = { viewModel.closeSongStory() }
            )
        }
    }
}

/**
 * 方案 1 极简顶部栏 (Apple Music / Spotify 旗舰设计规范)：
 * 左侧：极简毛玻璃折叠箭头 (KeyboardArrowDown)
 * 中间：副标题来源/状态优雅指示
 * 右侧：故事徽标 (✦ 故事) + 待播队列 + 更多设置
 */
@Composable
private fun NowPlayingTopBar(
    trackState: TrackPlaybackState,
    hasSongStory: Boolean,
    isTraditional: Boolean,
    isDark: Boolean,
    onOpenSongStory: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topGradBase = if (isDark) Color(0xFF090A10) else MaterialTheme.colorScheme.background
    val pillBg = if (isDark) Color(0xFF1E2230).copy(alpha = 0.92f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.10f)
    val pillIconTint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val strings = LocalStrings.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to topGradBase.copy(alpha = 0.98f),
                    0.65f to topGradBase.copy(alpha = 0.90f),
                    1.0f to Color.Transparent
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：优雅收起/设置图标按钮 + 正在播放歌曲缩略信息 (靠左排布)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Surface(
                color = pillBg,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, pillBorder),
                modifier = Modifier
                    .size(38.dp)
                    .bouncyIconClickable(onClick = onOpenSettings)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "收起/设置",
                        tint = pillIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (trackState.hasValidTrack) {
                if (!trackState.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = trackState.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val displayTitle = if (isTraditional) ChineseConverter.toTraditional(trackState.title) else trackState.title
                    val rawArtist = if (trackState.artist.isNotBlank()) trackState.artist else if (!trackState.album.isNullOrBlank()) trackState.album else ""
                    val displayArtist = if (isTraditional) ChineseConverter.toTraditional(rawArtist) else rawArtist
                    Text(
                        text = displayTitle,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayArtist,
                        color = if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = strings.appName,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // 右侧操作群：歌曲背景故事直达入口 (✦ 故事) + 待播队列 (Queue) + 桌面悬浮歌词 (PiP)
        val storyInfiniteTransition = rememberInfiniteTransition(label = "storyGlow")
        val storyGlowAlpha by storyInfiniteTransition.animateFloat(
            initialValue = 0.40f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "storyGlowAlpha"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 歌曲背景故事外层高频入口（当有故事时呈现柔和微光，1 步直达；带平滑淡入展开与折叠动效）
            AnimatedVisibility(
                visible = hasSongStory,
                enter = fadeIn(tween(220)) + expandHorizontally(spring(dampingRatio = 0.85f, stiffness = 420f)),
                exit = fadeOut(tween(160)) + shrinkHorizontally(tween(200))
            ) {
                val storyBg = if (isDark) Color(0xFF281E10).copy(alpha = 0.94f) else Color(0xFFFFF8E1).copy(alpha = 0.95f)
                val storyColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFF57C00)
                Surface(
                    color = storyBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        0.5.dp,
                        storyColor.copy(alpha = storyGlowAlpha)
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .bouncyClickable(
                            pressedScale = 0.93f,
                            onClick = onOpenSongStory
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = strings.storyBadge,
                            tint = storyColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = strings.storyBadge,
                            color = storyColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Surface(
                color = pillBg,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, pillBorder),
                modifier = Modifier
                    .size(38.dp)
                    .bouncyIconClickable(
                        onClick = onOpenQueue
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "待播队列",
                        tint = pillIconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 桌面悬浮歌词胶囊快捷切换按键 (灵动微胶囊)
            val context = LocalContext.current
            val isFloatingActive by FloatingLyricsService.isRunningFlow.collectAsState()

            Surface(
                color = if (isFloatingActive) Color(0xFF0288D1).copy(alpha = 0.85f) else pillBg,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, if (isFloatingActive) Color(0xFF81D4FA) else pillBorder),
                modifier = Modifier
                    .size(38.dp)
                    .bouncyIconClickable(
                        onClick = {
                            if (isFloatingActive) {
                                FloatingLyricsService.stop(context)
                            } else {
                                if (Settings.canDrawOverlays(context)) {
                                    FloatingLyricsService.start(context)
                                } else {
                                    FloatingLyricsService.requestOverlayPermission(context)
                                }
                            }
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "桌面悬浮歌词",
                        tint = if (isFloatingActive) Color.White else pillIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 丝滑流畅歌词滚动列表 (流体视差与羽化遮罩)
 * 1. 采用物理级几何中心对齐：动态计算歌词行垂直中点与视口黄金分割线 (36%) 的精确差值
 * 2. 硬件级 graphicsLayer 离屏羽化渐变遮罩 (CompositingStrategy.Offscreen + DstIn)，两端自然融入虚空
 * 3. 活跃行 27sp 纯白 Bold 聚光灯聚焦，非活跃行 20sp 半透柔光退居次席
 */
@Composable
private fun NowPlayingLyricsContent(
    lyrics: List<BilingualLyricLine>,
    currentLineIndex: Int,
    annotatedLines: Map<Int, LyricAnnotationEntity>,
    isPlaying: Boolean,
    isTraditional: Boolean,
    isLoadingLyrics: Boolean,
    isDark: Boolean,
    isAmoledMode: Boolean = false,
    trackState: TrackPlaybackState,
    lyricOffsetMs: Long = 0L,
    isWordByWordEnabled: Boolean = true,
    lyricAlignment: Int = AiPreferences.LYRIC_ALIGN_LEFT,
    showPlaybackControls: Boolean = true,
    onLineClicked: (BilingualLyricLine) -> Unit,
    onAnnotationClicked: (LyricAnnotationEntity) -> Unit,
    onRetryLyrics: () -> Unit = {},
    onOpenManualSearch: () -> Unit = {}
) {
    val strings = LocalStrings.current

    if (isLoadingLyrics && lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = if (isDark) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = strings.syncingLyricsStatus,
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = strings.noTimedLyricsFound,
                    color = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)),
                        modifier = Modifier
                            .bouncyClickable(pressedScale = 0.94f) { onRetryLyrics() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.retryBtn,
                                tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.retryBtn,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Surface(
                        color = if (isDark) Color(0xFF1E2235) else Color(0xFFE8EBF5),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF333852) else Color(0xFFD0D5E5)),
                        modifier = Modifier
                            .bouncyClickable(pressedScale = 0.94f) { onOpenManualSearch() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = strings.manualSearchLyricsBtn,
                                tint = if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.manualSearchLyricsBtn,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 60fps 高精度毫秒级播放进度采样器 (驱动活跃行 Apple Music 风格逐字光效扫掠)
    var currentEstimatedPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, trackState, lyricOffsetMs, isWordByWordEnabled) {
        if (isPlaying && isWordByWordEnabled) {
            while (isActive) {
                currentEstimatedPosition = (trackState.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
                delay(16) // 60fps 丝滑插值更新
            }
        } else {
            currentEstimatedPosition = (trackState.getEstimatedPositionMs() + lyricOffsetMs).coerceAtLeast(0L)
        }
    }

    // 采用统一流体物理弹簧规范 (450ms 优雅磁吸无机械顿挫)
    val lyricFluidSpring = remember {
        spring<Float>(
            dampingRatio = 0.86f, // 次临界柔和阻尼：零机械顿挫，优雅磁吸微缓冲
            stiffness = 180f      // 柔和流体刚度：~450ms 连续平滑位移，与人类语速天然共振
        )
    }

    // 监听用户真实手指触摸拖拽交互 (不被程序化滚动所污染)
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledAway by remember { mutableStateOf(false) }
    var lastUserDragReleaseTime by remember { mutableLongStateOf(0L) }

    // 当用户用手指触摸拖拽时，标记用户偏离当前播放位置；松手时记录时间戳
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            userScrolledAway = true
        } else if (userScrolledAway) {
            lastUserDragReleaseTime = System.currentTimeMillis()
        }
    }

    // 当用户手动滑动离开后长时间无操作（8秒以上）且歌曲推进到新歌词行时，优雅自动恢复跟随
    LaunchedEffect(currentLineIndex) {
        if (userScrolledAway && !isUserDragging && isPlaying) {
            val idleDuration = System.currentTimeMillis() - lastUserDragReleaseTime
            if (idleDuration >= 8000L) {
                userScrolledAway = false
            }
        }
    }

    // 动态判断当前视口是否包含正在播放的活跃行
    val isCurrentLineVisible by remember {
        derivedStateOf {
            currentLineIndex >= 0 && listState.layoutInfo.visibleItemsInfo.any { it.index == currentLineIndex }
        }
    }

    // 动态计算当前视口黄金视线带（约 36% 高度处）最中央的歌词行索引，用于视口相对阅读聚焦
    val visibleFocalIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf currentLineIndex.coerceAtLeast(0)

            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.36f else 300f
            val closest = visibleItems.minByOrNull { item ->
                val itemCenter = item.offset.toFloat() + (item.size.toFloat() / 2f)
                kotlin.math.abs(itemCenter - targetFocalY)
            }
            closest?.index ?: currentLineIndex.coerceAtLeast(0)
        }
    }

    // 当用户手动滑动离开后，若滑动回到当前播放行，则自动恢复跟随
    LaunchedEffect(visibleFocalIndex, currentLineIndex, isUserDragging) {
        if (!isUserDragging && userScrolledAway && visibleFocalIndex == currentLineIndex) {
            userScrolledAway = false
        }
    }

    // 用户处于手指拖拽或滑动偏离状态时进入自由浏览模式
    val isBrowsing = isUserDragging || userScrolledAway

    // 确定当前的视觉基准行：在翻阅浏览模式下以视口中央行为基准，跟随模式下以正在播放行为基准
    val focalIndex = if (isBrowsing) {
        visibleFocalIndex
    } else {
        if (currentLineIndex >= 0) currentLineIndex else visibleFocalIndex
    }

    // 首次载入歌词时，直接对齐至当前播放行几何中心 (若处于曲目前奏期，定位至首行预备)
    LaunchedEffect(lyrics.isNotEmpty()) {
        if (lyrics.isNotEmpty()) {
            val targetIndex = if (currentLineIndex in lyrics.indices) currentLineIndex else 0
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.36f else 300f
            listState.scrollToItem(targetIndex)
            val item = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            if (item != null && viewportHeight > 0f) {
                val itemCenterY = item.offset.toFloat() + (item.size.toFloat() / 2f)
                val scrollDelta = itemCenterY - targetFocalY
                if (kotlin.math.abs(scrollDelta) > 1.0f) {
                    listState.scrollBy(scrollDelta)
                }
            }
        }
    }

    // 物理级黄金视线重心对齐动力学 (流体顺滑吸附)
    // 监听当前歌词改变以及播放状态切换：跟随模式下每一行歌词切换均如丝绸般滑入焦点
    LaunchedEffect(currentLineIndex, isPlaying, userScrolledAway) {
        // 当暂停播放或用户正在手动翻阅时，绝对不强制回滚，完全保障自由沉浸
        if (!isPlaying || userScrolledAway) {
            return@LaunchedEffect
        }
        if (currentLineIndex in lyrics.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.36f else 300f
            val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }

            if (visibleItem != null && viewportHeight > 0f) {
                // 统一以活跃行的垂直几何中心与黄金重心对齐，彻底消除行高不同引起的上下颠簸
                val itemCenterY = visibleItem.offset.toFloat() + (visibleItem.size.toFloat() / 2f)
                val scrollDelta = itemCenterY - targetFocalY

                if (kotlin.math.abs(scrollDelta) > 1.0f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = lyricFluidSpring
                    )
                }
            } else {
                // 若由于跳转或初次加载导致当前行脱离视口，先顺畅滑动到目标行
                listState.animateScrollToItem(currentLineIndex)
                val itemAfter = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }
                if (itemAfter != null && viewportHeight > 0f) {
                    val itemCenterY = itemAfter.offset.toFloat() + (itemAfter.size.toFloat() / 2f)
                    val scrollDelta = itemCenterY - targetFocalY
                    if (kotlin.math.abs(scrollDelta) > 1.0f) {
                        listState.animateScrollBy(
                            value = scrollDelta,
                            animationSpec = lyricFluidSpring
                        )
                    }
                }
            }
        }
    }

    val isAlignLeft = lyricAlignment == AiPreferences.LYRIC_ALIGN_LEFT
    val listPadding = if (isAlignLeft) {
        PaddingValues(top = 135.dp, bottom = 220.dp, start = 20.dp, end = 48.dp)
    } else {
        PaddingValues(top = 135.dp, bottom = 220.dp, start = 24.dp, end = 24.dp)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // 硬件级上下边缘羽化蒙版 (Apple Music 纯净两端羽化虚空过渡)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.02f to Color.Transparent,
                            0.05f to Color.Black,
                            0.88f to Color.Black,
                            0.97f to Color.Transparent,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "${line.startTimeMs ?: index}_${line.original.hashCode()}" }
            ) { index, line ->
                val isActive = (index == currentLineIndex)
                val isFocal = (index == focalIndex)
                val distance = kotlin.math.abs(index - focalIndex)
                val annotation = annotatedLines[index]

                LyricLineRow(
                    line = line,
                    isActive = isActive,
                    isFocal = isFocal,
                    distance = distance,
                    isBrowsing = isBrowsing,
                    annotation = annotation,
                    isTraditional = isTraditional,
                    isDark = isDark,
                    isWordByWordEnabled = isWordByWordEnabled,
                    currentPositionMsProvider = { currentEstimatedPosition },
                    lyricAlignment = lyricAlignment,
                    onClick = {
                        userScrolledAway = false
                        lastUserDragReleaseTime = 0L
                        onLineClicked(line)
                    },
                    onAnnotationClick = {
                        if (annotation != null) {
                            val enriched = if (annotation.lyricTranslation.isNullOrBlank() && line.translation.isNotBlank()) {
                                annotation.copy(lyricTranslation = line.translation)
                            } else annotation
                            onAnnotationClicked(enriched)
                        }
                    }
                )
            }
        }

        // 浮动返回正在播放位置胶囊 (Apple Music / Spotify 旗舰级体验：脱离播放焦点且离开视口时温和出现，轻触瞬间丝滑回正)
        val showReturnPill = userScrolledAway && !isUserDragging && (visibleFocalIndex != currentLineIndex) && currentLineIndex in lyrics.indices && currentLineIndex >= 0
        AnimatedVisibility(
            visible = showReturnPill,
            enter = fadeIn(spring(dampingRatio = 0.86f, stiffness = 320f)) +
                    slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it / 2 } +
                    scaleIn(spring(dampingRatio = 0.86f, stiffness = 320f), initialScale = 0.90f),
            exit = fadeOut(tween(160)) +
                    slideOutVertically(tween(200)) { it / 2 } +
                    scaleOut(tween(160), targetScale = 0.90f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showPlaybackControls) 138.dp else 36.dp)
        ) {
            val isScrolledAbove = visibleFocalIndex < currentLineIndex
            Surface(
                color = if (isDark) {
                    if (isAmoledMode) Color(0xFF0C0C0E) else Color(0xFF1E202B).copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) {
                        if (isAmoledMode) Color(0xFF222226) else Color.White.copy(alpha = 0.16f)
                    } else {
                        Color.Black.copy(alpha = 0.10f)
                    }
                ),
                shadowElevation = 8.dp,
                modifier = Modifier.bouncyClickable(pressedScale = 0.93f) {
                    coroutineScope.launch {
                        // 核心：立即重置用户偏离标记，让播放进度跟随立刻接管！
                        userScrolledAway = false
                        lastUserDragReleaseTime = 0L

                        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                        val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.36f else 300f

                        val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }
                        if (visibleItem != null && viewportHeight > 0f) {
                            val itemCenterY = visibleItem.offset.toFloat() + (visibleItem.size.toFloat() / 2f)
                            val scrollDelta = itemCenterY - targetFocalY
                            if (kotlin.math.abs(scrollDelta) > 1.0f) {
                                listState.animateScrollBy(
                                    value = scrollDelta,
                                    animationSpec = lyricFluidSpring
                                )
                            }
                        } else {
                            listState.animateScrollToItem(currentLineIndex)
                            val itemAfter = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }
                            if (itemAfter != null && viewportHeight > 0f) {
                                val itemCenterY = itemAfter.offset.toFloat() + (itemAfter.size.toFloat() / 2f)
                                val scrollDelta = itemCenterY - targetFocalY
                                if (kotlin.math.abs(scrollDelta) > 1.0f) {
                                    listState.animateScrollBy(
                                        value = scrollDelta,
                                        animationSpec = lyricFluidSpring
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isScrolledAbove) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = strings.returnToPlayingPosition,
                        tint = if (isDark) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.returnToPlayingPosition,
                        color = if (isDark) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

/**
 * 单行双语歌词组件 (流体物理联动动画)
 */
@Composable
private fun LyricLineRow(
    line: BilingualLyricLine,
    isActive: Boolean,
    isFocal: Boolean,
    distance: Int,
    isBrowsing: Boolean,
    annotation: LyricAnnotationEntity?,
    isTraditional: Boolean,
    isDark: Boolean,
    isWordByWordEnabled: Boolean = true,
    currentPositionMsProvider: () -> Long = { 0L },
    lyricAlignment: Int = AiPreferences.LYRIC_ALIGN_LEFT,
    onClick: () -> Unit,
    onAnnotationClick: () -> Unit
) {
    // 空间景深动力学与自由阅读增强 (符合 WCAG AAA 标准，高对比聚光灯聚焦)：
    // 1. 活跃行拥有绝对聚光灯聚焦：纯白高对比度 (1.0f)，从左原点锚定缩放；
    // 2. 常规跟随状态下，非活跃行大幅柔化退居次席 (相邻行 0.38f，其余 0.18f)；
    // 3. 翻阅浏览模式下，视口中央行与周边行整体提升可读性。
    val targetOriginalAlpha = when {
        isBrowsing -> {
            when (distance) {
                0 -> 1.0f
                1 -> if (isDark) 0.85f else 0.82f
                2 -> if (isDark) 0.70f else 0.68f
                else -> if (isDark) 0.55f else 0.50f
            }
        }
        isActive -> 1.0f
        else -> {
            when (distance) {
                1 -> if (isDark) 0.40f else 0.38f
                2 -> if (isDark) 0.24f else 0.22f
                else -> if (isDark) 0.14f else 0.12f
            }
        }
    }

    val targetTransAlpha = when {
        isBrowsing -> {
            when (distance) {
                0 -> 0.95f
                1 -> if (isDark) 0.78f else 0.75f
                2 -> if (isDark) 0.60f else 0.58f
                else -> if (isDark) 0.45f else 0.42f
            }
        }
        isActive -> 0.90f
        else -> {
            when (distance) {
                1 -> if (isDark) 0.30f else 0.28f
                2 -> if (isDark) 0.18f else 0.16f
                else -> if (isDark) 0.10f else 0.08f
            }
        }
    }

    // 缩放策略：实时活跃行应用 1.06f 聚光灯聚焦，非活跃行 0.96f，左侧锚点 TransformOrigin(0f, 0.5f)
    val targetScale = when {
        isBrowsing -> if (distance == 0) 1.03f else 0.97f
        isActive -> 1.06f
        else -> 0.96f
    }

    // 采用与视口垂直滚动物理弹簧（dampingRatio = 0.86f, stiffness = 180f）完全相同曲线
    val lyricMotionSpring = remember {
        spring<Float>(
            dampingRatio = 0.86f,
            stiffness = 180f
        )
    }

    // Apple Music 电影级空间景深虚化 (Depth-of-field Blur)：
    // 聚焦行绝对清晰 (0dp)，相邻行微虚 (1.5dp)，外围行如梦似幻光斑退景 (3.5dp)；用户翻阅时全部解除虚化
    val targetBlur = when {
        isBrowsing || isActive -> 0.dp
        distance == 1 -> 1.5.dp
        else -> 3.5.dp
    }

    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 180f
        ),
        label = "lineBlur"
    )

    val animatedOriginalAlpha by animateFloatAsState(
        targetValue = targetOriginalAlpha,
        animationSpec = lyricMotionSpring,
        label = "origAlpha"
    )

    val animatedTransAlpha by animateFloatAsState(
        targetValue = targetTransAlpha,
        animationSpec = lyricMotionSpring,
        label = "transAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = lyricMotionSpring,
        label = "lineScale"
    )

    val displayOriginal = remember(line.original, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.original) else line.original
    }

    val displayTranslation = remember(line.translation, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.translation) else line.translation
    }

    val lyricColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
    val isAlignLeft = lyricAlignment == AiPreferences.LYRIC_ALIGN_LEFT
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = if (isAlignLeft) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
            }
            .blur(animatedBlur)
            .clip(RoundedCornerShape(14.dp))
            .bouncyPress(
                interactionSource = interactionSource,
                pressedScale = 0.985f,
                pressedAlpha = 0.96f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                ),
                onClick = {
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (e: Exception) {
                        // 忽略不支持设备
                    }
                    onClick()
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isAlignLeft) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // 1. 原文歌词 (Apple Music 纯净高对比双层连续流光排版)
        if (isActive && isWordByWordEnabled && line.words.isNotEmpty()) {
            AppleMusicDualLayerLyricLine(
                text = displayOriginal,
                words = line.words,
                currentPositionMsProvider = currentPositionMsProvider,
                lyricColor = lyricColor,
                isAlignLeft = isAlignLeft
            )
        } else {
            Text(
                text = displayOriginal,
                color = lyricColor,
                fontSize = if (isActive) 30.sp else 21.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = if (isActive) 40.sp else 30.sp,
                letterSpacing = if (isActive) (-0.6).sp else (-0.3).sp,
                textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = animatedOriginalAlpha }
            )
        }

        // 2. 中文翻译 (紧随原文，纯净对齐，清晰辨识)
        if (displayTranslation.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayTranslation,
                color = lyricColor,
                fontSize = if (isActive) 17.sp else 14.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                lineHeight = if (isActive) 24.sp else 20.sp,
                textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = animatedTransAlpha }
            )
        }

        // 3. 典故微胶囊徽标 (仅在活跃或用户翻阅聚焦行优雅展示，保持非活跃行纯净极简)
        val showAnnotation = annotation != null && (isActive || (isBrowsing && isFocal))
        if (showAnnotation) {
            Spacer(modifier = Modifier.height(6.dp))
            LyricAnnotationBadge(
                isActive = true,
                isDark = isDark,
                onClick = onAnnotationClick
            )
        }
    }
}

/**
 * Apple Music 官方原生双层连续流光渲染组件 (Apple Music-style Dual-Layer Continuous Sweep)
 *
 * 彻底废除生硬的独立词块拆分与逐字跳跃，1:1 还原 Apple Music 官方渲染机制：
 * 1. 统一文本排版：底层（Base Layer）与高光层（Highlight Layer）共用同一个完整文本布局，
 *    字间距、折行与字体度量 100% 严丝合缝，零文字晃动；
 * 2. 液体水平流光遮罩 (Liquid Continuous Mask)：
 *    在 GPU 离屏图层 (CompositingStrategy.Offscreen) 上利用 BlendMode.DstIn 绘制
 *    随毫秒进度平滑推进的羽化渐变遮罩，使亮白光泽如水银抚过字符；
 * 3. 多行自动换行适配：多行文本按行独立计算起止 X 轴，前一行常亮，当前行流光扫掠，后续行等待；
 * 4. 基线绝对稳固：文字完全依附于排版基线，杜绝任何怪异的垂直跳动与弹跳。
 */
@Composable
private fun AppleMusicDualLayerLyricLine(
    text: String,
    words: List<com.linernotes.app.domain.model.LyricWord>,
    currentPositionMsProvider: () -> Long,
    lyricColor: Color,
    isAlignLeft: Boolean,
    modifier: Modifier = Modifier
) {
    val currentPositionMs = currentPositionMsProvider()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 计算各词在纯文本中的字符起止范围
    val wordCharSpans = remember(text, words) {
        var charCursor = 0
        words.map { w ->
            val cleanWord = LyricWordParser.cleanInlineTimestamps(w.text)
            val len = cleanWord.length.coerceAtLeast(1)
            val start = charCursor.coerceAtMost(text.length)
            val end = (start + len).coerceAtMost(text.length)
            charCursor = end
            Triple(w, start, end)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isAlignLeft) Alignment.TopStart else Alignment.TopCenter
    ) {
        // 底层：35% 半透明暗底文本 (提供前瞻未唱状态)
        Text(
            text = text,
            color = lyricColor.copy(alpha = 0.35f),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 40.sp,
            letterSpacing = (-0.6).sp,
            textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // 高光层：100% 亮白文本 + 柔和白炽辉光 + 硬件级 DstIn 连续扫光遮罩
        Text(
            text = text,
            color = lyricColor,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 40.sp,
            letterSpacing = (-0.6).sp,
            textAlign = if (isAlignLeft) TextAlign.Start else TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = lyricColor.copy(alpha = 0.28f),
                    offset = Offset.Zero,
                    blurRadius = 10f
                )
            ),
            onTextLayout = { result ->
                textLayoutResult = result
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()

                    val layout = textLayoutResult
                    if (layout == null || words.isEmpty()) {
                        return@drawWithContent
                    }

                    val firstStart = words.first().startTimeMs
                    val lastEnd = words.last().startTimeMs + words.last().durationMs

                    if (currentPositionMs < firstStart) {
                        // 尚未开唱：全遮罩透明，高光层不显现
                        drawRect(
                            color = Color.Transparent,
                            blendMode = BlendMode.DstIn
                        )
                        return@drawWithContent
                    }

                    if (currentPositionMs >= lastEnd) {
                        // 整句演唱完毕：保留全部高光层
                        return@drawWithContent
                    }

                    // 定位当前正在演唱的词/字
                    val activeIndex = wordCharSpans.indexOfFirst { (w, _, _) ->
                        currentPositionMs >= w.startTimeMs && currentPositionMs < (w.startTimeMs + w.durationMs)
                    }.let { idx ->
                        if (idx >= 0) idx
                        else {
                            val lastPassed = wordCharSpans.indexOfLast { (w, _, _) -> currentPositionMs >= w.startTimeMs }
                            if (lastPassed >= 0) lastPassed else 0
                        }
                    }

                    val (activeWord, startChar, endChar) = wordCharSpans[activeIndex]
                    val intraProgress = if (activeWord.durationMs > 0) {
                        ((currentPositionMs - activeWord.startTimeMs).toFloat() / activeWord.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 1f

                    val charSpan = (endChar - startChar).coerceAtLeast(1)
                    val floatCharOffset = (startChar + charSpan * intraProgress).coerceIn(0f, text.length.toFloat())
                    val baseCharOffset = floatCharOffset.toInt().coerceIn(0, (text.length - 1).coerceAtLeast(0))
                    val nextCharOffset = (baseCharOffset + 1).coerceAtMost(text.length)
                    val fraction = floatCharOffset - baseCharOffset

                    val currentLine = layout.getLineForOffset(baseCharOffset)
                    val charLeftX = layout.getHorizontalPosition(baseCharOffset, true)
                    val nextLine = if (nextCharOffset < text.length) layout.getLineForOffset(nextCharOffset) else currentLine
                    val charRightX = if (nextLine == currentLine && nextCharOffset < text.length) {
                        layout.getHorizontalPosition(nextCharOffset, true)
                    } else {
                        layout.getLineRight(currentLine)
                    }
                    val sweepX = charLeftX + (charRightX - charLeftX) * fraction.coerceIn(0f, 1f)

                    val fadeWidth = 28.dp.toPx()

                    for (l in 0 until layout.lineCount) {
                        val lineTop = layout.getLineTop(l)
                        val lineBottom = layout.getLineBottom(l)
                        val lineHeight = lineBottom - lineTop
                        val lineL = layout.getLineLeft(l)

                        if (l < currentLine) {
                            // 已唱完的行：保持完全不透明 (亮白常亮)
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(0f, lineTop),
                                size = Size(size.width, lineHeight),
                                blendMode = BlendMode.DstIn
                            )
                        } else if (l == currentLine) {
                            // 正在演唱的行：从行首至 sweepX 进行羽化渐变
                            val startFade = (sweepX - fadeWidth * 0.45f).coerceAtLeast(lineL)
                            val endFade = (sweepX + fadeWidth * 0.55f).coerceAtMost(size.width)

                            val stop1 = (startFade / size.width).coerceIn(0f, 1f)
                            val stop2 = (endFade / size.width).coerceIn(stop1, 1f)

                            val brush = Brush.horizontalGradient(
                                0.0f to Color.White,
                                stop1 to Color.White,
                                stop2 to Color.Transparent,
                                1.0f to Color.Transparent,
                                startX = 0f,
                                endX = size.width
                            )
                            drawRect(
                                brush = brush,
                                topLeft = Offset(0f, lineTop),
                                size = Size(size.width, lineHeight),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        // l > currentLine 保持未绘制透明 (在 Layer 2 上完全透明，底层 35% 显现)
                    }
                }
        )
    }
}

/**
 * 珠宝级香槟金磨砂微胶囊典故徽标 (流媒体原生微交互设计)
 */
@Composable
private fun LyricAnnotationBadge(
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    // 活跃行柔和呼吸光晕
    val infiniteTransition = rememberInfiniteTransition(label = "badgeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val badgeBg = if (isActive) {
        if (isDark) Color(0xFFFFD54F).copy(alpha = 0.16f) else Color(0xFFFFE082).copy(alpha = 0.35f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    }
    val badgeBorder = if (isActive) {
        if (isDark) Color(0xFFFFD54F).copy(alpha = glowAlpha) else Color(0xFFFFA000).copy(alpha = glowAlpha)
    } else {
        if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    }
    val badgeContent = if (isActive) {
        if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
    } else {
        if (isDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }

    Surface(
        color = badgeBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.5.dp,
            badgeBorder
        ),
        modifier = Modifier
            .bouncyClickable(
                pressedScale = 0.90f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = strings.triviaBadge,
                tint = badgeContent,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = strings.triviaBadge,
                color = badgeContent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * 方案 1：底栏悬浮毛玻璃全能胶囊 (极简流体悬浮坞)
 * 上半部：当前进度时间 01:24 + 极细平滑声学进度条 (3dp 无白块) + 总时长 04:36
 * 下半部：歌曲封面缩略图 (44dp) + 歌曲名(支持跑马灯) + 歌手名 + 随机 / 上一首 / 播放·暂停 / 下一首
 */
@Composable
private fun NowPlayingFloatingGlassPlayer(
    trackState: TrackPlaybackState,
    isTraditional: Boolean,
    isDark: Boolean,
    isAmoledMode: Boolean = false,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var estimatedPosition by remember { mutableLongStateOf(trackState.currentPositionMs) }

    LaunchedEffect(trackState) {
        while (isActive) {
            estimatedPosition = trackState.getEstimatedPositionMs()
            delay(100)
        }
    }

    val duration = trackState.durationMs.coerceAtLeast(1L)
    val progress = (estimatedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    val playerBg = if (isDark) {
        if (isAmoledMode) Color(0xFF0C0C0E) else Color(0xFF0F1118).copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val playerBorder = if (isDark) {
        if (isAmoledMode) Color(0xFF222226) else Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    val onPlayerText = if (isDark) Color.White else Color(0xFF111218)
    val onPlayerTextSec = if (isDark) Color.White.copy(alpha = 0.60f) else Color(0xFF656772)

    val displayTitle = remember(trackState.title, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(trackState.title) else trackState.title
    }
    val displayArtist = remember(trackState.artist, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(trackState.artist) else trackState.artist
    }
    val strings = LocalStrings.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            color = playerBg,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(0.5.dp, playerBorder),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 上半部：极细时间指示与平滑时间轴 (纯净 3dp 进度线与 8dp 小圆点，杜绝粗大白块)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(estimatedPosition),
                        color = onPlayerTextSec,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.Start
                    )

                    SleekTrackScrubber(
                        progress = progress,
                        isDark = isDark,
                        onSeekToFraction = { frac ->
                            val targetMs = (frac * duration).toLong()
                            onSeekTo(targetMs)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    Text(
                        text = formatMs(duration),
                        color = onPlayerTextSec.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.End
                    )
                }

                // 下半部：歌曲封面缩略图 + 歌曲信息 + 播放控制群
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 歌曲封面微缩图 + 歌名/歌手群 (占用超大横向空间，绝不吞字截断)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // 1. 歌曲封面缩略图 (38dp 黄金微胶囊，点击查看待播队列)
                        val coverScale by animateFloatAsState(
                            targetValue = if (trackState.hasValidTrack) 1.0f else 0.94f,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
                            label = "coverScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .graphicsLayer {
                                    scaleX = coverScale
                                    scaleY = coverScale
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .border(BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)), RoundedCornerShape(10.dp))
                                .bouncyClickable(
                                    pressedScale = 0.90f,
                                    onClick = onOpenQueue
                                )
                        ) {
                            if (!trackState.coverUrl.isNullOrBlank()) {
                                val context = LocalContext.current
                                val coverRequest = remember(trackState.coverUrl) {
                                    ImageRequest.Builder(context)
                                        .data(trackState.coverUrl)
                                        .crossfade(false)
                                        .build()
                                }
                                AsyncImage(
                                    model = coverRequest,
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Surface(
                                    color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isDark) Color.White.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.50f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 歌曲信息 (宽阔横向可视区，前 2.6 秒完全静止展示完整歌名，绝不截断)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable(
                                    pressedScale = 0.96f,
                                    onClick = onOpenSettings
                                )
                        ) {
                            Text(
                                text = if (trackState.hasValidTrack) displayTitle else strings.appName,
                                color = onPlayerText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-0.3).sp,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 2600,
                                    repeatDelayMillis = 1500,
                                    velocity = 26.dp
                                )
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = if (trackState.hasValidTrack) displayArtist else LocalStrings.current.waitingForPlaybackShort,
                                color = onPlayerTextSec,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 3000,
                                    repeatDelayMillis = 1500,
                                    velocity = 24.dp
                                )
                            )
                        }
                    }

                    // 3. 播放控制按键群 (紧凑精致流体物理弹簧，释放出最大宽度给歌名)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        // 随机播放按键
                        val shuffleBg by animateColorAsState(
                            targetValue = if (trackState.isShuffleActive) Color(0xFF1ED760).copy(alpha = 0.20f) else Color.Transparent,
                            animationSpec = spring(stiffness = 600f),
                            label = "shuffleBg"
                        )
                        val shuffleTint by animateColorAsState(
                            targetValue = if (trackState.isShuffleActive) Color(0xFF1ED760) else onPlayerTextSec,
                            animationSpec = spring(stiffness = 600f),
                            label = "shuffleTint"
                        )
                        Surface(
                            color = shuffleBg,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(26.dp)
                                .bouncyIconClickable(
                                    onClick = onToggleShuffle
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (trackState.isShuffleActive) "随机播放 (已开启)" else "顺序播放",
                                    tint = shuffleTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // 上一首
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .bouncyIconClickable(
                                    onClick = onPrevious
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                tint = if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF22242B),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 播放/暂停 Hero 圆钮 (38dp 饱满白瓷高光，带物理弹簧与丝滑形态形变)
                        val heroScale by animateFloatAsState(
                            targetValue = if (trackState.isPlaying) 1.04f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.80f, stiffness = 500f),
                            label = "heroScale"
                        )
                        val heroElevation by animateDpAsState(
                            targetValue = if (trackState.isPlaying) 10.dp else 4.dp,
                            animationSpec = spring(dampingRatio = 0.80f, stiffness = 400f),
                            label = "heroElevation"
                        )

                        Surface(
                            color = if (isDark) Color.White else Color(0xFF19191C),
                            shape = CircleShape,
                            shadowElevation = heroElevation,
                            modifier = Modifier
                                .size(38.dp)
                                .graphicsLayer {
                                    scaleX = heroScale
                                    scaleY = heroScale
                                }
                                .bouncyIconClickable(
                                    pressedScale = 0.88f,
                                    onClick = onPlayPause
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState = trackState.isPlaying,
                                    transitionSpec = {
                                        fadeIn(tween(130, easing = LinearOutSlowInEasing)) + scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 600f)) togetherWith
                                            fadeOut(tween(110)) + scaleOut(targetScale = 0.72f)
                                    },
                                    label = "playPauseMorph"
                                ) { isPlaying ->
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "暂停" else "播放",
                                        tint = if (isDark) Color(0xFF0F1118) else Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }

                        // 下一首
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .bouncyIconClickable(
                                    onClick = onNext
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                tint = if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF22242B),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 极简平滑声学进度轨 (Apple Music / Spotify 3dp 极细流线，附带手指触碰即刻微绽放的触感动力学)
 */
@Composable
private fun SleekTrackScrubber(
    progress: Float,
    isDark: Boolean,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = (if (isDragging) dragFraction else progress).coerceIn(0f, 1f)

    // 触感物理绽放动力学：手指拖拽时微膨胀，松手时平滑沉降回极细流线
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 4.5.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "scrubberTrackHeight"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 13.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 480f),
        label = "scrubberThumbSize"
    )

    val trackBaseColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.14f)
    val trackActiveColor = if (isDark) Color.White else Color(0xFF19191C)
    val thumbColor = if (isDark) Color.White else Color(0xFF19191C)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (e: Exception) {}
                    onSeekToFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (e: Exception) {}
                    },
                    onDragEnd = {
                        isDragging = false
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (e: Exception) {}
                        onSeekToFraction(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragFraction = frac
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        // 槽轨底色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(trackBaseColor)
        )

        // 已播放高光槽轨
        Box(
            modifier = Modifier
                .fillMaxWidth(currentFraction)
                .height(trackHeight)
                .clip(CircleShape)
                .background(trackActiveColor)
        )

        // 极细圆形滑块指示点 (具有拖拽触感绽放与高光边框)
        val thumbRadiusPx = with(LocalDensity.current) { (thumbSize / 2).toPx() }
        val thumbOffsetDp = with(LocalDensity.current) {
            ((widthPx * currentFraction) - thumbRadiusPx).coerceIn(0f, (widthPx - thumbRadiusPx * 2).coerceAtLeast(0f)).toDp()
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.20f)
                    ),
                    CircleShape
                )
        )
    }
}

/**
 * 空闲等待页 (干净纯粹的 Spotify 引导)
 */
@Composable
private fun NowPlayingIdleContent(
    isDark: Boolean,
    isNotificationGranted: Boolean,
    onGrantPermission: () -> Unit,
    onLaunchSpotify: () -> Unit
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.35f)),
            shadowElevation = 12.dp,
            modifier = Modifier.size(92.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.appName,
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.waitingForPlaybackSubtitle,
            color = if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isNotificationGranted) {
            Surface(
                color = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF1DB954).copy(alpha = 0.30f) else Color(0xFF1DB954).copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.openNotificationPermission,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = strings.openNotificationPermissionDesc,
                            color = if (isDark) Color.White.copy(alpha = 0.50f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Surface(
                        color = Color(0xFF1DB954),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .bouncyClickable(
                                pressedScale = 0.92f,
                                onClick = onGrantPermission
                            )
                    ) {
                        Text(
                            text = strings.grantPermissionAction,
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        Surface(
            color = Color(0xFF1DB954),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .bouncyClickable(
                    pressedScale = 0.96f,
                    onClick = onLaunchSpotify
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.openMusicAppBtn,
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
