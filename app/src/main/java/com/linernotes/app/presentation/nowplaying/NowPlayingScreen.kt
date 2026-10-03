package com.linernotes.app.presentation.nowplaying

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0E))
    ) {
        // 1. 灵动流体弥散背景 (Apple Music / Lyricify 风格，纯净温润呼吸)
        AmbientGlowBackground(
            coverUrl = trackState.coverUrl,
            isDark = true,
            modifier = Modifier.fillMaxSize()
        )

        // 2. 核心主内容区 (歌词区域全屏贯通至底栏下方，呈现极致通透感)
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (hasTrack) {
                NowPlayingLyricsContent(
                    lyrics = nowData.lyrics,
                    currentLineIndex = nowData.currentLineIndex,
                    annotatedLines = nowData.annotatedLines,
                    isTraditional = state.isTraditionalChinese,
                    isLoadingLyrics = nowData.isLoadingLyrics,
                    onLineClicked = { line ->
                        line.startTimeMs?.let { viewModel.seekTo(it) }
                    },
                    onAnnotationClicked = { annotation ->
                        viewModel.openGeniusAnnotation(annotation)
                    }
                )
            } else {
                NowPlayingIdleContent(
                    isNotificationGranted = isGranted.value,
                    onGrantPermission = {
                        MediaPlaybackSyncService.openNotificationAccessSettings(context)
                        isGranted.value = MediaPlaybackSyncService.isNotificationAccessGranted(context)
                    },
                    onLaunchSpotify = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                            context.startActivity(webIntent)
                        }
                    }
                )
            }

            // 3. 极简顶部状态微栏 (为歌词留出 80% 屏幕纵向空间，集成外层歌曲故事直达入口)
            NowPlayingTopBar(
                trackState = trackState,
                hasSongStory = nowData.songStory != null,
                onOpenSongStory = { viewModel.openSongStory() },
                onOpenQueue = { viewModel.openQueueSheet() },
                onOpenSettings = { viewModel.openSettings() },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 4. Genius 获取失败/状态提醒微浮标 (iOS Dynamic Island 悬浮微胶囊风格)
            AnimatedVisibility(
                visible = nowData.geniusNoticeMessage != null && hasTrack,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { -it / 2 } + scaleIn(initialScale = 0.94f),
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 2 } + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = Color(0xFF1E202B).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💡", fontSize = 12.sp)
                        Text(
                            text = nowData.geniusNoticeMessage ?: "",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "重试",
                            color = Color(0xFF1ED760),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .bouncyClickable(pressedScale = 0.92f) { viewModel.retryGenius() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White.copy(alpha = 0.5f),
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
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipToNext() },
                    onPrevious = { viewModel.skipToPrevious() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onSeekTo = { posMs -> viewModel.seekTo(posMs) },
                    onOpenQueue = { viewModel.openQueueSheet() },
                    onOpenSettings = { viewModel.openSettings() }
                )
            }
        }

        // 待播队列 (Queue) 底部抽屉 (仿 Spotify 队列界面)
        if (state.isQueueSheetOpen) {
            NowPlayingQueueSheet(
                trackState = trackState,
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onPlayPause = { viewModel.togglePlayPause() },
                onDismiss = { viewModel.closeQueueSheet() }
            )
        }

        // 统一设置抽屉 (包含时间轴微调、繁简、假名、控制栏开关等)
        if (state.isSettingsSheetOpen) {
            LyricNotesSettingsSheet(
                trackState = trackState,
                hasSongStory = nowData.songStory != null,
                geniusNotice = nowData.geniusNoticeMessage,
                furiganaMode = state.furiganaMode,
                isTraditionalChinese = state.isTraditionalChinese,
                isDeCensorEnabled = state.isDeCensorEnabled,
                showPlaybackControls = state.showPlaybackControls,
                lyricOffsetMs = state.lyricOffsetMs,
                onOpenSongStory = { viewModel.openSongStory() },
                onReloadGenius = { viewModel.retryGenius() },
                onAdjustOffset = { delta -> viewModel.adjustLyricOffset(delta) },
                onResetOffset = { viewModel.resetLyricOffset() },
                onSetFuriganaMode = { mode -> viewModel.setFuriganaMode(mode) },
                onToggleTraditionalChinese = { viewModel.toggleTraditionalChinese() },
                onToggleDeCensor = { viewModel.toggleDeCensor() },
                onTogglePlaybackControls = { viewModel.togglePlaybackControls() },
                onReloadLyrics = { viewModel.reloadLyrics() },
                onDismiss = { viewModel.closeSettings() }
            )
        }

        // Genius Behind The Lyrics 底部抽屉
        if (state.isGeniusSheetOpen) {
            NowPlayingGeniusSheet(
                annotation = state.selectedAnnotation,
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
    onOpenSongStory: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0xFF090A10).copy(alpha = 0.98f),
                    0.65f to Color(0xFF090A10).copy(alpha = 0.90f),
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
            color = Color(0xFF1E2230).copy(alpha = 0.92f),
            shape = CircleShape,
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)),
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
                    tint = Color.White,
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
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp
            )
            if (trackState.hasValidTrack && !trackState.album.isNullOrBlank()) {
                Text(
                    text = trackState.album,
                    color = Color.White.copy(alpha = 0.90f),
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
                Surface(
                    color = Color(0xFF281E10).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        0.5.dp,
                        Color(0xFFFFD54F).copy(alpha = storyGlowAlpha)
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
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "故事",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Surface(
                color = Color(0xFF1E2230).copy(alpha = 0.92f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
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
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Surface(
                color = Color(0xFF1E2230).copy(alpha = 0.92f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
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
                        tint = Color.White.copy(alpha = 0.90f),
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
    onLineClicked: (BilingualLyricLine) -> Unit,
    onAnnotationClicked: (LyricAnnotationEntity) -> Unit
) {
    if (isLoadingLyrics && lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "正在同步歌词...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无带时间轴的歌词",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif
            )
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

    // 物理级黄金聚焦点对齐动力学 (对标 Lyricify / Apple Music 视频中的流体顺滑滑动与零回弹)
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex in lyrics.indices) {
            val isInteracting = (System.currentTimeMillis() - lastUserInteractionTime) < 2500L && listState.isScrollInProgress
            if (isInteracting) {
                return@LaunchedEffect
            }

            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            // 黄金聚焦基准线：视口高度的 32%（顶部三分之一处），与视频中的视线重心完美对齐
            val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.32f else 300f
            val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }

            if (visibleItem != null && viewportHeight > 0f) {
                // 统一以活跃行的顶部为基准线对齐，杜绝因行数不同导致的上下晃动
                val currentTop = visibleItem.offset.toFloat()
                val scrollDelta = currentTop - targetFocalY

                if (kotlin.math.abs(scrollDelta) > 1.5f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f 临界阻尼：消除机械回弹与颠簸，精准平稳滑停！
                            stiffness = 220f                            // 流体刚度：丝滑滑行动效耗时约 460ms，对标 Apple Music 优雅如水
                        )
                    )
                }
            } else {
                listState.animateScrollToItem(
                    index = currentLineIndex.coerceAtLeast(0),
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
                    // 顶部 14% 彻底透明，歌词在到达顶栏下方之前就彻底淡出，绝不与顶栏按钮重叠！
                    // 底部 14% 彻底透明，歌词在靠近底栏之前完全隐形，绝不与底栏播放器冲突！
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.14f to Color.Transparent,
                            0.22f to Color.Black,
                            0.78f to Color.Black,
                            0.86f to Color.Transparent,
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
                    onClick = { onLineClicked(line) },
                    onAnnotationClick = { annotation?.let(onAnnotationClicked) }
                )
            }
        }
    }
}

/**
 * 单行双语歌词组件 (高对比度纯正无杂色，对标 Lyricify 视觉重心)
 */
@Composable
private fun LyricLineRow(
    line: BilingualLyricLine,
    isActive: Boolean,
    distance: Int,
    annotation: LyricAnnotationEntity?,
    isTraditional: Boolean,
    onClick: () -> Unit,
    onAnnotationClick: () -> Unit
) {
    // 严格遵循 Apple Music / Lyricify 原生规范：仅当前活跃行高亮聚焦，非活跃行保持静止统一暗白，
    // 彻底消除上下各行层级联动淡入淡出的晃眼杂乱动效（杜绝“上下都动画”）
    val animatedOriginalAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.38f,
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        ),
        label = "origAlpha"
    )

    // 中文翻译透明度动力学：活跃行 88% 清晰对照，非活跃行 28% 雅致弱化
    val animatedTransAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.88f else 0.28f,
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        ),
        label = "transAlpha"
    )

    val displayTranslation = remember(line.translation, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.translation) else line.translation
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyItemClickable(pressedScale = 0.985f, onClick = onClick)
    ) {
        // 原文歌词：统一固定字号与行高，彻底消除动态测量导致的整列上下抖动与颠簸！
        // 视觉重心完全由 Weight (Bold vs Medium) 与 Alpha (100% vs 45%) 优雅表达
        Text(
            text = line.original,
            color = Color.White.copy(alpha = animatedOriginalAlpha),
            fontSize = 24.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 33.sp,
            letterSpacing = (-0.3).sp
        )

        // 中文翻译：统一固定字号与清晰行高
        if (displayTranslation.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayTranslation,
                color = Color.White.copy(alpha = animatedTransAlpha),
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 22.sp
            )
        }

        // 典故微胶囊徽标 (作为自然脚注排布在歌词正下方，左对齐不挤压横向文本)
        if (annotation != null) {
            Spacer(modifier = Modifier.height(7.dp))
            LyricAnnotationBadge(
                isActive = isActive,
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

    Surface(
        color = if (isActive) Color(0xFFFFD54F).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.5.dp,
            if (isActive) Color(0xFFFFD54F).copy(alpha = glowAlpha) else Color.White.copy(alpha = 0.14f)
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
                tint = if (isActive) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = "典故",
                color = if (isActive) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.75f),
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            color = Color(0xFF0F1118).copy(alpha = 0.94f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)),
            shadowElevation = 16.dp,
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
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.Start
                    )

                    SleekTrackScrubber(
                        progress = progress,
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
                        color = Color.White.copy(alpha = 0.50f),
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
                    // 歌曲封面微缩图 + 歌名/歌手群
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 歌曲封面缩略图 (44dp 黄金微胶囊，点击查看待播队列)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(12.dp))
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
                                    color = Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.70f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 歌曲信息 (支持超长跑马灯滚动，点击打开设置)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable(
                                    pressedScale = 0.96f,
                                    onClick = onOpenSettings
                                )
                        ) {
                            Text(
                                text = if (trackState.hasValidTrack) trackState.title else "LyricNotes",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-0.2).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = if (trackState.hasValidTrack) trackState.artist else "等待播放",
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 3. 播放控制按键群 (纯正流体弹簧触感按压)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 随机播放按键
                        Surface(
                            color = if (trackState.isShuffleActive) Color(0xFF1ED760).copy(alpha = 0.18f) else Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(34.dp)
                                .bouncyIconClickable(
                                    onClick = onToggleShuffle
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (trackState.isShuffleActive) "随机播放 (已开启)" else "顺序播放",
                                    tint = if (trackState.isShuffleActive) Color(0xFF1ED760) else Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 上一首
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .bouncyIconClickable(
                                    onClick = onPrevious
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 播放/暂停 Hero 圆钮 (42dp 饱满白瓷高光)
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .size(42.dp)
                                .bouncyIconClickable(
                                    pressedScale = 0.90f,
                                    onClick = onPlayPause
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (trackState.isPlaying) "暂停" else "播放",
                                    tint = Color(0xFF0F1118),
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        // 下一首
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .bouncyIconClickable(
                                    onClick = onNext
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(22.dp)
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
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = (if (isDragging) dragFraction else progress).coerceIn(0f, 1f)

    // 触感物理绽放动力学：手指拖拽时微膨胀，松手时平滑沉降回极细流线
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 4.5.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "scrubberTrackHeight"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "scrubberThumbSize"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekToFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
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

        // 槽轨底色 (20% 半透白)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f))
        )

        // 已播放高光槽轨 (100% 纯白)
        Box(
            modifier = Modifier
                .fillMaxWidth(currentFraction)
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White)
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
                .background(Color.White)
        )
    }
}

/**
 * 空闲等待页 (干净纯粹的 Spotify 引导)
 */
@Composable
private fun NowPlayingIdleContent(
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
            color = Color.White.copy(alpha = 0.06f),
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
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "在 Spotify 开始播放音乐，实时双语歌词与\nGenius 典故将自动呈现在屏幕上",
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isNotificationGranted) {
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.30f)),
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
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "用于获取 Spotify 播放进度与歌词同屏",
                            color = Color.White.copy(alpha = 0.50f),
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
