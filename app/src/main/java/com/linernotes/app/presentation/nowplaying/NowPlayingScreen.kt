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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.linernotes.app.presentation.common.bouncyClickable
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
            .background(Color(0xFF0F0F12))
    ) {
        // 1. 灵动流体弥散背景 (Apple Music / Lyricify 风格)
        AmbientGlowBackground(
            coverUrl = trackState.coverUrl,
            isDark = true,
            modifier = Modifier.fillMaxSize()
        )

        // 柔和暗色纵深遮罩 (避免遮挡流光，同时确保文字可读性)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // 2. 核心主内容区 (歌词区域全屏贯通至底栏下方，呈现极致毛玻璃通透感)
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

            // 4. Genius 获取失败/状态提醒微浮标 (Gemini / TG 悬浮微胶囊风格)
            AnimatedVisibility(
                visible = nowData.geniusNoticeMessage != null && hasTrack,
                enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { -it },
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
                                .clickable { viewModel.retryGenius() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.dismissGeniusNotice() }
                        )
                    }
                }
            }

            // 5. 方案 1：底栏悬浮毛玻璃全能胶囊 (复刻 LinerNotes 经典底栏，集合歌曲信息、滑条与播控)
            AnimatedVisibility(
                visible = hasTrack && state.showPlaybackControls,
                enter = fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 2 },
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
                isTraditional = state.isTraditionalChinese,
                onDismiss = { viewModel.closeGeniusSheet() }
            )
        }

        // 歌曲背景故事抽屉
        if (state.isSongStorySheetOpen) {
            NowPlayingSongStorySheet(
                story = nowData.songStory,
                isTraditional = state.isTraditionalChinese,
                onDismiss = { viewModel.closeSongStory() }
            )
        }
    }
}

/**
 * 方案 1 极简顶部栏：
 * 左侧：LYRICNOTES 品牌微标与 Spotify 同步状态指示
 * 右侧：纯粹的 "···" 更多设置按钮
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
                    colors = listOf(
                        Color(0xFF0F0F12),
                        Color(0xFF0F0F12).copy(alpha = 0.85f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧微标：Gemini / TG 风格毛玻璃胶囊状态徽标
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier.bouncyClickable(
                pressedScale = 0.94f,
                onClick = onOpenSettings
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (trackState.isPlaying) Color(0xFF1ED760) else Color.White.copy(alpha = 0.4f))
                )
                Text(
                    text = "LYRICNOTES",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.6.sp
                )
            }
        }

        // 右侧操作群：歌曲背景故事直达入口 (📖 故事) + 待播队列 (Queue) + 更多设置 (···)
        val storyInfiniteTransition = rememberInfiniteTransition(label = "storyGlow")
        val storyGlowAlpha by storyInfiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.70f,
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
            Surface(
                color = if (hasSongStory) Color(0xFFFFD54F).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    0.5.dp,
                    if (hasSongStory) Color(0xFFFFD54F).copy(alpha = storyGlowAlpha) else Color.White.copy(alpha = 0.14f)
                ),
                modifier = Modifier
                    .height(36.dp)
                    .bouncyClickable(
                        pressedScale = 0.92f,
                        onClick = onOpenSongStory
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "歌曲故事",
                        tint = if (hasSongStory) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "故事",
                        color = if (hasSongStory) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = if (hasSongStory) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClickable(
                        pressedScale = 0.90f,
                        onClick = onOpenQueue
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "待播队列",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClickable(
                        pressedScale = 0.90f,
                        onClick = onOpenSettings
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "设置",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * 丝滑流畅歌词滚动列表 (高对比度、清晰防遮挡排版)
 * 1. 采用物理级几何中心对齐：动态计算歌词行垂直中点与视口黄金分割线 (34%) 的精确差值
 * 2. 硬件级 graphicsLayer 缩放与透明度变换，杜绝跳行重绘抖动
 * 3. 增强非活跃行与翻译行对比度（从 10% 提升至 35%~65%），确保全屏歌词清晰舒适可读
 * 4. 加高顶部与底部遮罩，避免与手机状态栏发生文字重叠穿透
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

    // 物理级黄金聚焦点对齐动力学 (Lyricify 经典 Spring 弹性物理阻尼与低刚度阻尼衰减)
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex in lyrics.indices) {
            val isInteracting = (System.currentTimeMillis() - lastUserInteractionTime) < 2500L && listState.isScrollInProgress
            if (isInteracting) {
                return@LaunchedEffect
            }

            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            val targetFocalY = if (viewportHeight > 0f) viewportHeight * 0.34f else 300f
            val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == currentLineIndex }

            if (visibleItem != null && viewportHeight > 0f) {
                val currentCenter = visibleItem.offset.toFloat() + (visibleItem.size.toFloat() / 2f)
                val scrollDelta = currentCenter - targetFocalY

                if (kotlin.math.abs(scrollDelta) > 1.0f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = spring(
                            dampingRatio = 0.82f, // 黄金次临界阻尼：消除机械顿挫感，平滑衰减沉降
                            stiffness = 240f      // 柔和低刚度：丝滑滑动耗时约 520ms，如水般流淌
                        )
                    )
                }
            } else {
                val focalOffset = -(targetFocalY - 40f).toInt()
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = focalOffset.coerceAtMost(0)
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 110.dp, bottom = 220.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize()
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

        // 顶部柔和渐隐暗角遮罩 (控制在 90dp 范围内，仅阻隔系统状态栏，绝不遮盖第一行歌词)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F12).copy(alpha = 0.90f),
                            Color(0xFF0F0F12).copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部渐隐暗角遮罩 (使歌词顺畅淡入毛玻璃播放胶囊之后)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF0F0F12).copy(alpha = 0.75f),
                            Color(0xFF0F0F12)
                        )
                    )
                )
        )
    }
}

/**
 * 单行双语歌词组件 (高对比度纯正无杂色，彻底告别看不清)
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
    // Lyricify / Apple Music 经典动力学：活跃行 1.035f 微弹聚焦，非活跃行 0.95f，左边缘锚定，弹性平滑缓动
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isActive -> 1.035f
            distance == 1 -> 0.985f
            else -> 0.95f
        },
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = 320f
        ),
        label = "lyricScale"
    )

    // 原文透明度动力学：活跃行 100% 纯白锁定，相邻行 70%，远行 38%~52%，清晰可读杜绝暗黑发灰
    val animatedOriginalAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1.0f
            distance == 1 -> 0.70f
            distance == 2 -> 0.52f
            else -> 0.38f
        },
        animationSpec = tween(
            durationMillis = 380,
            easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
        ),
        label = "origAlpha"
    )

    // 中文翻译透明度动力学：独立控制透明度（避免与外层叠加相乘导致只有3%~6%），活跃行 95%，相邻行 64%，远行 34%~46%
    val animatedTransAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 0.95f
            distance == 1 -> 0.64f
            distance == 2 -> 0.46f
            else -> 0.34f
        },
        animationSpec = tween(
            durationMillis = 380,
            easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
        ),
        label = "transAlpha"
    )

    val displayTranslation = remember(line.translation, isTraditional) {
        if (isTraditional) ChineseConverter.toTraditional(line.translation) else line.translation
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 原文歌词 (纯白高对比，饱满视觉重心)
                Text(
                    text = line.original,
                    color = Color.White.copy(alpha = animatedOriginalAlpha),
                    fontSize = 26.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.3).sp
                )

                // 中文翻译 (舒适字号与清晰行高，直接采用独立透明度，绝不发黑看不清)
                if (displayTranslation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = displayTranslation,
                        color = Color.White.copy(alpha = animatedTransAlpha),
                        fontSize = 14.5.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 21.sp
                    )
                }
            }

            // 极简灵动微胶囊典故徽标 (替代原粗重黄色圆圈，打造珠宝级香槟金磨砂微标)
            if (annotation != null) {
                LyricAnnotationBadge(
                    isActive = isActive,
                    onClick = onAnnotationClick
                )
            }
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
        color = if (isActive) Color(0xFFFFD54F).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.5.dp,
            if (isActive) Color(0xFFFFD54F).copy(alpha = glowAlpha) else Color.White.copy(alpha = 0.16f)
        ),
        modifier = Modifier
            .padding(start = 12.dp, top = 2.dp)
            .bouncyClickable(
                pressedScale = 0.90f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
 * 方案 1：底栏悬浮毛玻璃全能胶囊 (复刻 LinerNotes 经典底栏手感)
 * 上半部：当前进度时间 01:24 + 极细平滑滑块 + 总时长 04:36
 * 下半部：专辑微图 (38dp) + 歌曲名 + 歌手名 + 上一首 / 播放·暂停 / 下一首
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
            color = Color(0xFF141622).copy(alpha = 0.88f),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 上半部：时间与极细滑块
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(estimatedPosition),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Start
                    )

                    Slider(
                        value = progress,
                        onValueChange = { newProg ->
                            val targetMs = (newProg * duration).toLong()
                            onSeekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(horizontal = 4.dp)
                    )

                    Text(
                        text = formatMs(duration),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }

                // 下半部：歌曲信息与控制键 (Gemini 悬浮胶囊排布)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 歌曲信息群 (点击封面查看待播队列，点击文字打开设置)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.bouncyClickable(
                                pressedScale = 0.92f,
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
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(11.dp))
                                )
                            } else {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

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
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = if (trackState.hasValidTrack) trackState.artist else "等待播放",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 播放按键群 (单按键切换随机/顺序播放 + 上一首 + Telegram/Gemini 纯白高光播放圆钮 + 下一首)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 随机/顺序播放切换按键 (点击切换，开启时呈现 Spotify 绿胶囊徽标，关闭时极简半透白)
                        Surface(
                            color = if (trackState.isShuffleActive) Color(0xFF1ED760).copy(alpha = 0.18f) else Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(32.dp)
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    onClick = onToggleShuffle
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (trackState.isShuffleActive) "随机播放 (已开启)" else "顺序播放 (已开启)",
                                    tint = if (trackState.isShuffleActive) Color(0xFF1ED760) else Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 上一首 (Previous)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    onClick = onPrevious
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 播放/暂停 (Hero Circle Play/Pause Button)
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(38.dp)
                                .bouncyClickable(
                                    pressedScale = 0.88f,
                                    onClick = onPlayPause
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (trackState.isPlaying) "暂停" else "播放",
                                    tint = Color(0xFF12131C),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 下一首 (Next)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    onClick = onNext
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
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
            color = Color.White.copy(alpha = 0.08f),
            shape = CircleShape,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "LyricNotes",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "在 Spotify 开始播放音乐，实时双语歌词与\nGenius 典故将自动呈现在屏幕上",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isNotificationGranted) {
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.4f)),
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
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Button(
                        onClick = onGrantPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("去开启", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        Button(
            onClick = onLaunchSpotify,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = "打开 Spotify 播放音乐 ➔",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
