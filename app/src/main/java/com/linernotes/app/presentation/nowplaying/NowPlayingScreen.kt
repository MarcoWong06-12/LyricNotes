package com.linernotes.app.presentation.nowplaying

import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linernotes.app.core.lyric.LyricAligner
import com.linernotes.app.core.playback.MediaPlaybackSyncService
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.presentation.booklet.components.AmbientGlowBackground
import com.linernotes.app.presentation.nowplaying.components.NowPlayingGeniusSheet
import com.linernotes.app.presentation.nowplaying.components.NowPlayingSongStorySheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onNavigateToShelf: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val nowData = state.nowPlayingData
    val trackState = nowData.playbackState
    val context = LocalContext.current

    val hasTrack = trackState.hasValidTrack
    val isGranted = remember { mutableStateOf(MediaPlaybackSyncService.isNotificationAccessGranted(context)) }

    // 检查通知权限
    LaunchedEffect(Unit) {
        isGranted.value = MediaPlaybackSyncService.isNotificationAccessGranted(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101014))) {
        // 1. 动态高斯模糊流体弥散背景 (Apple Music 风格)
        AmbientGlowBackground(
            coverUrl = trackState.coverUrl,
            isDark = true,
            modifier = Modifier.fillMaxSize()
        )

        // 黑色柔和遮罩增强对比度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                NowPlayingTopBar(
                    trackState = trackState,
                    hasStory = nowData.songStory != null,
                    furiganaMode = state.furiganaMode,
                    onOpenStory = { viewModel.openSongStory() },
                    onCycleFurigana = { viewModel.cycleFuriganaMode() },
                    onNavigateToShelf = onNavigateToShelf
                )
            },
            bottomBar = {
                if (hasTrack) {
                    NowPlayingBottomBar(
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
                                val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com"))
                                context.startActivity(webIntent)
                            }
                        },
                        onNavigateToShelf = onNavigateToShelf
                    )
                }
            }
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

@Composable
private fun NowPlayingTopBar(
    trackState: com.linernotes.app.core.playback.TrackPlaybackState,
    hasStory: Boolean,
    furiganaMode: FuriganaDisplayMode,
    onOpenStory: () -> Unit,
    onCycleFurigana: () -> Unit,
    onNavigateToShelf: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Spotify 状态药丸标
        Surface(
            color = if (trackState.isSpotify) Color(0xFF1DB954).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
            shape = CircleShape,
            border = BorderStroke(1.dp, if (trackState.isSpotify) Color(0xFF1DB954).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (trackState.isPlaying) Color(0xFF1ED760) else Color.Gray)
                )
                Text(
                    text = trackState.sourceApp.displayName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 中间曲名提示
        if (trackState.hasValidTrack) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clickable { onOpenStory() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = trackState.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = trackState.artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // 右侧操作按钮组
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hasStory) {
                IconButton(onClick = onOpenStory, modifier = Modifier.size(36.dp)) {
                    Text("📖", fontSize = 16.sp)
                }
            }

            IconButton(onClick = onCycleFurigana, modifier = Modifier.size(36.dp)) {
                Surface(
                    color = if (furiganaMode != FuriganaDisplayMode.OFF) Color(0xFF1DB954) else Color.White.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "あ",
                            color = if (furiganaMode != FuriganaDisplayMode.OFF) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = onNavigateToShelf, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = "唱片架",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingLyricsContent(
    lyrics: List<BilingualLyricLine>,
    currentLineIndex: Int,
    annotatedLines: Map<Int, com.linernotes.app.data.local.entity.LyricAnnotationEntity>,
    isLoadingLyrics: Boolean,
    onLineClicked: (BilingualLyricLine) -> Unit,
    onAnnotationClicked: (com.linernotes.app.data.local.entity.LyricAnnotationEntity) -> Unit
) {
    if (isLoadingLyrics && lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Color(0xFF1DB954), strokeWidth = 3.dp)
                Text("正在多源拉取歌词与自动翻译补全...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂未检索到带时间轴的歌词", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        return
    }

    val listState = rememberLazyListState()

    // 自动平滑滚动对齐当前正在播放行
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -220
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = (index == currentLineIndex)
            val annotation = annotatedLines[index]

            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.05f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "lyricScale"
            )

            val textAlpha by animateFloatAsState(
                targetValue = if (isActive) 1.0f else 0.42f,
                animationSpec = tween(durationMillis = 300),
                label = "lyricAlpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
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
                            color = Color.White.copy(alpha = textAlpha),
                            fontSize = if (isActive) 23.sp else 18.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            lineHeight = if (isActive) 32.sp else 26.sp
                        )

                        if (line.translation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = line.translation,
                                color = if (isActive) Color(0xFFFFD54F) else Color.White.copy(alpha = textAlpha * 0.8f),
                                fontSize = if (isActive) 15.sp else 13.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // 典故黄色微光小标签
                    if (annotation != null) {
                        Surface(
                            color = Color(0xFFFFC107).copy(alpha = if (isActive) 0.25f else 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onAnnotationClicked(annotation) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("💡", fontSize = 11.sp)
                                Text("典故", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBottomBar(
    trackState: com.linernotes.app.core.playback.TrackPlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    // 毫秒级防漂移时间推算
    var estimatedPosition by remember { mutableLongStateOf(trackState.currentPositionMs) }

    LaunchedEffect(trackState) {
        while (isActive) {
            estimatedPosition = trackState.getEstimatedPositionMs()
            kotlinx.coroutines.delay(100)
        }
    }

    val duration = trackState.durationMs.coerceAtLeast(1L)
    val progress = (estimatedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // 进度条与时间显示
        Slider(
            value = progress,
            onValueChange = { newProg ->
                val newPos = (newProg * duration).toLong()
                estimatedPosition = newPos
                onSeekTo(newPos)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth().height(20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = LyricAligner.formatTimestamp(estimatedPosition).replace("[", "").replace("]", "").substringBeforeLast("."),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = LyricAligner.formatTimestamp(duration).replace("[", "").replace("]", "").substringBeforeLast("."),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 播控按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            Surface(
                color = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(62.dp)
                    .clickable { onPlayPause() },
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (trackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (trackState.isPlaying) "暂停" else "播放",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(28.dp))

            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingIdleContent(
    isNotificationGranted: Boolean,
    onGrantPermission: () -> Unit,
    onLaunchSpotify: () -> Unit,
    onNavigateToShelf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = CircleShape,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("💿", fontSize = 48.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "LyricNotes 已就绪",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "在 Spotify 或其他播放器开始播放音乐，\n我们将自动实时同步双语歌词与 Genius 典故",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 步骤 1：权限授权卡片
        Surface(
            color = if (isNotificationGranted) Color(0xFF1DB954).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isNotificationGranted) Color(0xFF1DB954).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isNotificationGranted) "✓ 媒体会话监听已授权" else "步骤 1：开启通知使用权",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isNotificationGranted) "可以自动实时感知切歌与进度" else "用于获取 Spotify 当前播放歌曲与毫秒进度",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                if (!isNotificationGranted) {
                    Button(
                        onClick = onGrantPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("去授权", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 步骤 2：启动 Spotify
        Button(
            onClick = onLaunchSpotify,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("打开 Spotify 播放音乐 ➔", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        TextButton(onClick = onNavigateToShelf) {
            Text("或者浏览已收藏的 CD 唱片架", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
