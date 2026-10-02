package com.linernotes.app.presentation.nowplaying

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.linernotes.app.core.playback.MediaPlaybackSyncService
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.presentation.booklet.components.AmbientGlowBackground
import com.linernotes.app.presentation.nowplaying.components.LyricNotesSettingsSheet
import com.linernotes.app.presentation.nowplaying.components.NowPlayingGeniusSheet
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

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                NowPlayingTopBar(
                    trackState = trackState,
                    onOpenSettings = { viewModel.openSettings() }
                )
            },
            bottomBar = {
                // 仅当用户在设置中开启播放控制器时才显示，默认纯净全屏沉浸歌词
                AnimatedVisibility(
                    visible = hasTrack && state.showPlaybackControls,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 2 }
                ) {
                    NowPlayingFloatingControls(
                        trackState = trackState,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.skipToNext() },
                        onPrevious = { viewModel.skipToPrevious() },
                        onSeekTo = { posMs -> viewModel.seekTo(posMs) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
            }
        }

        // 统一设置抽屉 (包含时间轴微调、繁简、假名、控制栏开关等)
        if (state.isSettingsSheetOpen) {
            LyricNotesSettingsSheet(
                trackState = trackState,
                hasSongStory = nowData.songStory != null,
                furiganaMode = state.furiganaMode,
                isTraditionalChinese = state.isTraditionalChinese,
                isDeCensorEnabled = state.isDeCensorEnabled,
                showPlaybackControls = state.showPlaybackControls,
                lyricOffsetMs = state.lyricOffsetMs,
                onOpenSongStory = { viewModel.openSongStory() },
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
                onDismiss = { viewModel.closeGeniusSheet() }
            )
        }

        // 歌曲背景故事抽屉
        if (state.isSongStorySheetOpen) {
            NowPlayingSongStorySheet(
                story = nowData.songStory,
                onDismiss = { viewModel.closeSongStory() }
            )
        }
    }
}

/**
 * 极简顶部栏 (Lyricify 纯粹设计)
 * 左侧：精致圆角封面 + 歌曲名 + 歌手名
 * 右侧：单个纯粹的 "..." 更多设置按钮
 */
@Composable
private fun NowPlayingTopBar(
    trackState: TrackPlaybackState,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：封面图 + 曲名与歌手
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!trackState.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = trackState.coverUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (trackState.hasValidTrack) trackState.title else "LyricNotes",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (trackState.hasValidTrack) trackState.artist else "等待播放",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧：单个纯净 "..." 设置按钮
        Surface(
            color = Color.White.copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onOpenSettings)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 丝滑流畅歌词滚动列表 (Apple Music / Lyricify 风格)
 * 1. 采用固定字体排版 + 硬件级 graphicsLayer 缩放与透明度变换，杜绝跳行重绘抖动
 * 2. 居中平滑对齐当前句，过去与未来歌词优雅多层淡出
 * 3. 译文采用纯净白透现代排版
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
                    color = Color(0xFF1DB954),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "正在多源同步歌词...",
                    color = Color.White.copy(alpha = 0.6f),
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
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // 优雅居中平滑滚动 (使用固定 contentPadding 配合 offset 0，杜绝负偏移跳动)
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = 0
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 160.dp, bottom = 260.dp, start = 22.dp, end = 22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = (index == currentLineIndex)
            val distance = if (currentLineIndex >= 0) kotlin.math.abs(index - currentLineIndex) else 999
            val annotation = annotatedLines[index]

            // 硬件级缩放动效 (左边缘锚定，不抖动不换行)
            val animatedScale by animateFloatAsState(
                targetValue = if (isActive) 1.05f else 0.94f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 220f
                ),
                label = "lyricScale"
            )

            // 依据与活跃行距离的多阶平滑透明度过渡
            val animatedAlpha by animateFloatAsState(
                targetValue = when {
                    isActive -> 1.0f
                    distance == 1 -> 0.45f
                    distance == 2 -> 0.28f
                    else -> 0.16f
                },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "lyricAlpha"
            )

            val animatedTransAlpha by animateFloatAsState(
                targetValue = when {
                    isActive -> 0.78f
                    distance == 1 -> 0.32f
                    else -> 0.12f
                },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
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
                        alpha = animatedAlpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLineClicked(line) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = line.original,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 32.sp
                        )

                        if (displayTranslation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayTranslation,
                                color = Color.White.copy(alpha = animatedTransAlpha),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = 21.sp
                            )
                        }
                    }

                    // 极简灵动 Genius 典故标
                    if (annotation != null) {
                        Surface(
                            color = Color(0xFFFFD54F).copy(alpha = if (isActive) 0.22f else 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                Color(0xFFFFD54F).copy(alpha = if (isActive) 0.55f else 0.25f)
                            ),
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clickable { onAnnotationClicked(annotation) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("💡", fontSize = 11.sp)
                                Text(
                                    text = "典故",
                                    color = Color(0xFFFFE082),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
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

/**
 * 现代轻量悬浮式播放条 (可在设置中按需开启，不遮挡大面积歌词)
 */
@Composable
private fun NowPlayingFloatingControls(
    trackState: TrackPlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Surface(
            color = Color(0xFF18181D).copy(alpha = 0.88f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧进度微条
                Slider(
                    value = progress,
                    onValueChange = { newProg ->
                        val targetMs = (newProg * duration).toLong()
                        onSeekTo(targetMs)
                    },
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF1DB954),
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                // 播放控制三键
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(onClick = onPlayPause)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (trackState.isPlaying) "暂停" else "播放",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
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
