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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.core.playback.MediaPlaybackSyncService
import com.linernotes.app.core.playback.TrackPlaybackState
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

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF090A0E) else MaterialTheme.colorScheme.background)
    ) {
        // 1. 灵动流体弥散背景 (Apple Music / Lyricify 风格，纯净温润呼吸)
        AmbientGlowBackground(
            coverUrl = trackState.coverUrl,
            isDark = isDark,
            modifier = Modifier.fillMaxSize()
        )

        // 2. 核心主内容区 (歌词全屏贯通，并在呼出底部抽屉时呈现 iOS / Apple Music 规范的流体视差微下沉)
        val isAnySheetOpen = state.isSettingsSheetOpen || state.isGeniusSheetOpen || state.isSongStorySheetOpen || state.isQueueSheetOpen
        val contentParallaxScale by animateFloatAsState(
            targetValue = if (isAnySheetOpen) 0.958f else 1.0f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
            label = "sheetParallaxScale"
        )
        val contentParallaxAlpha by animateFloatAsState(
            targetValue = if (isAnySheetOpen) 0.84f else 1.0f,
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
                    isTraditional = state.isTraditionalChinese,
                    isLoadingLyrics = nowData.isLoadingLyrics,
                    isDark = isDark,
                    onLineClicked = onLineClicked,
                    onAnnotationClicked = onAnnotationClicked,
                    onRetryLyrics = onRetryLyrics
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
                    color = if (isDark) Color(0xFF1E202B).copy(alpha = 0.94f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (nowData.isLoadingGenius) "⏳" else "💡", fontSize = 12.sp)
                        val noticeMsg = if (nowData.isLoadingGenius) {
                            if (state.isTraditionalChinese) "正在檢索 Genius 典故與背景故事..." else "正在检索 Genius 典故与背景故事..."
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
                                    text = if (state.isTraditionalChinese) "檢索中" else "检索中",
                                    color = geniusAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        } else {
                            val geniusAccent = if (isDark) Color(0xFF1ED760) else Color(0xFF0A8F3F)
                            Text(
                                text = if (state.isTraditionalChinese) "重試" else "重试",
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
                            contentDescription = "关闭",
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
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onPlayPause = onPlayPause,
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
                onToggleDeCensor = { viewModel.toggleDeCensor() },
                onTogglePlaybackControls = { viewModel.togglePlaybackControls() },
                onReloadLyrics = { viewModel.reloadLyrics() },
                onDismiss = { viewModel.closeSettings() }
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
        // 左侧：优雅收起/设置图标按钮 (Apple Music 标准顶部收起图标，高对比磨砂底色)
        Surface(
            color = pillBg,
            shape = CircleShape,
            border = BorderStroke(0.5.dp, pillBorder),
            modifier = Modifier
                .size(38.dp)
                .bouncyIconClickable(
                    onClick = onOpenSettings
                )
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

        // 中间：极简优雅来源/状态信息
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = if (trackState.hasValidTrack) "正在播放" else "LyricNotes",
                color = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp
            )
            if (trackState.hasValidTrack && !trackState.album.isNullOrBlank()) {
                Text(
                    text = trackState.album,
                    color = if (isDark) Color.White.copy(alpha = 0.90f) else MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 右侧操作群：歌曲背景故事直达入口 (✦ 故事) + 待播队列 (Queue) + 更多设置 (···)
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
            // 歌曲背景故事外层高频入口（当有故事时呈现柔和微光，1 步直达）
            if (hasSongStory) {
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
                            contentDescription = "歌曲故事",
                            tint = storyColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "故事",
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

            Surface(
                color = pillBg,
                shape = CircleShape,
                border = BorderStroke(0.5.dp, pillBorder),
                modifier = Modifier
                    .size(38.dp)
                    .bouncyIconClickable(
                        onClick = onOpenSettings
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "设置",
                        tint = pillIconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * 丝滑流畅歌词滚动列表 (Apple Music / Lyricify 级视差与羽化遮罩)
 * 1. 采用物理级几何中心对齐：动态计算歌词行垂直中点与视口黄金分割线 (36%) 的精确差值
 * 2. 硬件级 graphicsLayer 离屏羽化渐变遮罩 (CompositingStrategy.Offscreen + DstIn)，两端自然融入虚空
 * 3. 活跃行 27sp 纯白 Bold 聚光灯聚焦，非活跃行 20sp 半透柔光退居次席
 */
@Composable
private fun NowPlayingLyricsContent(
    lyrics: List<BilingualLyricLine>,
    currentLineIndex: Int,
    annotatedLines: Map<Int, LyricAnnotationEntity>,
    isTraditional: Boolean,
    isLoadingLyrics: Boolean,
    isDark: Boolean,
    onLineClicked: (BilingualLyricLine) -> Unit,
    onAnnotationClicked: (LyricAnnotationEntity) -> Unit,
    onRetryLyrics: () -> Unit = {}
) {
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
                    text = if (isTraditional) "正在同步多源歌詞..." else "正在同步多源歌词...",
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
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = if (isTraditional) "暫無帶時間軸的歌詞" else "暂无带时间轴的歌词",
                    color = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Surface(
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)),
                    modifier = Modifier
                        .bouncyClickable(pressedScale = 0.94f) { onRetryLyrics() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新检索",
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isTraditional) "重新檢索歌詞" else "重新检索歌词",
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    var lastUserInteractionTime by remember { mutableLongStateOf(0L) }

    // 监听用户主动滑动浏览，防止自动对齐与用户手势发生拉扯
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            lastUserInteractionTime = System.currentTimeMillis()
        }
    }

    // 物理级黄金视线重心对齐动力学 (对标 Spotify 与 Apple Music 顶级流体顺滑吸附)
    // 监听当前歌词改变以及滑动状态切换：当用户停止滑动后经过 2 秒空闲，自动且平滑回归焦点
    LaunchedEffect(currentLineIndex, listState.isScrollInProgress) {
        if (currentLineIndex in lyrics.indices) {
            if (listState.isScrollInProgress) {
                return@LaunchedEffect
            }
            val timeSinceInteraction = System.currentTimeMillis() - lastUserInteractionTime
            if (timeSinceInteraction < 2000L) {
                kotlinx.coroutines.delay(2000L - timeSinceInteraction)
            }
            if (listState.isScrollInProgress) {
                return@LaunchedEffect
            }

            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            // 黄金视线重心：视口高度的 38%（Spotify 经典视觉焦点阅读带）
            val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.38f else 320f
            val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }

            if (visibleItem != null && viewportHeight > 0f) {
                // 统一以活跃行的垂直几何中心与黄金重心对齐，彻底消除行高不同引起的上下颠簸
                val itemCenterY = visibleItem.offset.toFloat() + (visibleItem.size.toFloat() / 2f)
                val scrollDelta = itemCenterY - targetFocalY

                if (kotlin.math.abs(scrollDelta) > 1.5f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = spring(
                            dampingRatio = 0.86f, // 次临界柔和阻尼：零回弹超临界平稳悬停，消除机械顿挫
                            stiffness = 160f      // 柔和流体刚度：~550ms 连续平滑位移，如轨道摄影机般平滑推进
                        )
                    )
                }
            } else {
                // 当条目因跳转或切歌脱离视口时，先快速平滑粗定位到视口内部，随后下一帧精确微调
                val approxPrecedingItems = (targetFocalY / 220f).toInt().coerceIn(1, 3)
                listState.scrollToItem(
                    index = (currentLineIndex - approxPrecedingItems).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 135.dp, bottom = 220.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // 硬件级上下边缘羽化蒙版：
                    // 顶部 7% 完全透明，7%~17% 渐变过渡；底部 80%~93% 柔和淡出融入悬浮底栏
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.07f to Color.Transparent,
                            0.17f to Color.Black,
                            0.80f to Color.Black,
                            0.93f to Color.Transparent,
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
                val distance = if (currentLineIndex >= 0) kotlin.math.abs(index - currentLineIndex) else index
                val annotation = annotatedLines[index]

                LyricLineRow(
                    line = line,
                    isActive = isActive,
                    distance = distance,
                    annotation = annotation,
                    isTraditional = isTraditional,
                    isDark = isDark,
                    onClick = { onLineClicked(line) },
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
    }
}

/**
 * 单行双语歌词组件 (高对比度纯正无杂色，RenderNode GPU 级合成动画)
 */
@Composable
private fun LyricLineRow(
    line: BilingualLyricLine,
    isActive: Boolean,
    distance: Int,
    annotation: LyricAnnotationEntity?,
    isTraditional: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onAnnotationClick: () -> Unit
) {
    // 严格遵循 Spotify & Apple Music 空间景深动力学：距离活跃行越远，字阶与透明度平滑向深空沉底
    val targetOriginalAlpha = when {
        isActive -> 1.0f
        distance == 1 -> 0.46f
        distance == 2 -> 0.28f
        distance == 3 -> 0.18f
        else -> (0.14f - (distance * 0.015f)).coerceAtLeast(0.06f)
    }

    val targetTransAlpha = when {
        isActive -> 0.86f
        distance == 1 -> 0.36f
        distance == 2 -> 0.20f
        distance == 3 -> 0.12f
        else -> (0.10f - (distance * 0.012f)).coerceAtLeast(0.04f)
    }

    val targetScale = when {
        isActive -> 1.035f
        distance == 1 -> 0.985f
        else -> 0.965f
    }

    // 与视口滚动动效（stiffness 175f）完全同频同步，消除“文字先变亮变大、再慢吞吞移动”的时间错位
    val animatedOriginalAlpha by animateFloatAsState(
        targetValue = targetOriginalAlpha,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 175f),
        label = "origAlpha"
    )

    val animatedTransAlpha by animateFloatAsState(
        targetValue = targetTransAlpha,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 175f),
        label = "transAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 185f),
        label = "lineScale"
    )

    val displayOriginal = remember(line.original, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.original) else line.original
    }

    val displayTranslation = remember(line.translation, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.translation) else line.translation
    }

    val lyricColor = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .bouncyItemClickable(pressedScale = 0.985f, onClick = onClick)
    ) {
        // 原文歌词：统一使用坚实一致的 FontWeight.Bold，杜绝因 Medium/Bold 突变导致的字宽重排与抽搐
        Text(
            text = displayOriginal,
            color = lyricColor,
            fontSize = 24.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 33.sp,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.graphicsLayer { alpha = animatedOriginalAlpha }
        )

        // 中文翻译：统一固定字号与字重，通过 graphicsLayer alpha 驱动渲染
        if (displayTranslation.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayTranslation,
                color = lyricColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 22.sp,
                modifier = Modifier.graphicsLayer { alpha = animatedTransAlpha }
            )
        }

        // 典故微胶囊徽标 (作为自然脚注排布在歌词正下方，左对齐不挤压横向文本)
        if (annotation != null) {
            Spacer(modifier = Modifier.height(7.dp))
            LyricAnnotationBadge(
                isActive = isActive,
                isDark = isDark,
                onClick = onAnnotationClick
            )
        }
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
                contentDescription = "典故",
                tint = badgeContent,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = "典故",
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
 * 方案 1：底栏悬浮毛玻璃全能胶囊 (Apple Music / Lyricify 风格极简悬浮坞)
 * 上半部：当前进度时间 01:24 + 极细平滑声学进度条 (3dp 无白块) + 总时长 04:36
 * 下半部：歌曲封面缩略图 (44dp) + 歌曲名(支持跑马灯) + 歌手名 + 随机 / 上一首 / 播放·暂停 / 下一首
 */
@Composable
private fun NowPlayingFloatingGlassPlayer(
    trackState: TrackPlaybackState,
    isTraditional: Boolean,
    isDark: Boolean,
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

    val playerBg = if (isDark) Color(0xFF0F1118).copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val playerBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val onPlayerText = if (isDark) Color.White else Color(0xFF111218)
    val onPlayerTextSec = if (isDark) Color.White.copy(alpha = 0.60f) else Color(0xFF656772)

    val displayTitle = remember(trackState.title, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(trackState.title) else trackState.title
    }
    val displayArtist = remember(trackState.artist, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(trackState.artist) else trackState.artist
    }

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
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(trackState.coverUrl)
                                        .crossfade(true)
                                        .build(),
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
                                text = if (trackState.hasValidTrack) displayTitle else "LyricNotes",
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
                                text = if (trackState.hasValidTrack) displayArtist else (if (isTraditional) "等待播放" else "等待播放"),
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
            text = "LyricNotes",
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "在 Spotify 开始播放音乐，实时双语歌词与\nGenius 典故将自动呈现在屏幕上",
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
                            text = "开启通知使用权",
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "用于获取 Spotify 播放进度与歌词同屏",
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
                            text = "去开启",
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
                        text = "打开 Spotify 播放音乐",
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
