package com.linernotes.app.presentation.booklet

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.presentation.booklet.components.AmbientGlowBackground
import com.linernotes.app.presentation.booklet.components.InlineLyricAnnotationCard
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.core.lyric.FuriganaEngine
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.presentation.booklet.components.*
import com.linernotes.app.presentation.booklet.model.AnnotationLoadState
import com.linernotes.app.presentation.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricBookletScreen(
    albumId: String,
    onNavigateBack: () -> Unit,
    viewModel: LyricBookletViewModel = hiltViewModel()
) {
    LaunchedEffect(albumId) {
        viewModel.setAlbumId(albumId)
    }
    val state by viewModel.uiState.collectAsState()
    val batchState by viewModel.batchTranslationState.collectAsState()
    val isBatchTranslatingThisAlbum = batchState.isTranslating && batchState.albumId == state.albumWithTracks?.album?.id
    val isTranslatingOverall = state.isTranslating || isBatchTranslatingThisAlbum
    val shelfAlbums by viewModel.allShelfAlbums.collectAsState()
    val bookletPages by viewModel.bookletPages.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var isCoverViewerOpen by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    val handleBackAction: () -> Unit = {
        when {
            state.expandedAnnotationLineIndex != null -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.collapseInlineAnnotation()
            }
            state.isImmersiveMode -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.setImmersiveMode(false)
            }
            state.selectedDiscogsDetail != null -> viewModel.closeDiscogsReleaseDetail()
            state.isDiscogsDetailLoading -> viewModel.closeDiscogsReleaseDetail()
            state.isDiscogsPickerOpen -> viewModel.openDiscogsPicker(false)
            state.isAnnotationSheetOpen -> viewModel.dismissAnnotationSheet()
            isCoverViewerOpen -> isCoverViewerOpen = false
            state.isEditingSheetOpen -> viewModel.openEditSheet(false)
            state.isSettingsOpen -> viewModel.openSettings(false)
            state.isCdMatchAlbumOpen -> viewModel.openCdMatchAlbum(false)
            state.isCdTracklistOpen -> viewModel.openCdTracklist(false)
            state.isCdSheetOpen -> viewModel.openCdSheet(false)
            state.isBookletSheetOpen -> viewModel.openBookletSheet(false)
            else -> onNavigateBack()
        }
    }

    // 适配 Android 系统手势导航 (全面屏边缘侧滑返回上一级)
    BackHandler(enabled = true) {
        handleBackAction()
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.connectCdPlayer()
        }
    }

    val requestCdConnect: (android.bluetooth.BluetoothDevice?) -> Unit = { targetDevice ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.connectCdPlayer(targetDevice)
            } else {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            viewModel.connectCdPlayer(targetDevice)
        }
    }

    val currentTrack = viewModel.getCurrentTrack()
    val bookletTracksCount = state.albumWithTracks?.tracks?.size ?: 0
    val totalTracks = if (bookletTracksCount > 0) bookletTracksCount else state.cdTotalTracks

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // 当伴侣播放器推进歌词时间轴时，平滑自动滚动居中聚焦当前活动行
    LaunchedEffect(state.activeLineIndex, state.songStory != null) {
        if (state.activeLineIndex in state.alignedLyrics.indices) {
            val targetIndex = if (state.songStory != null) state.activeLineIndex + 1 else state.activeLineIndex
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -220
            )
        }
    }

    // 切换曲目时立即复位歌词列表视口至顶部
    LaunchedEffect(state.currentTrackIndex) {
        listState.scrollToItem(0)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = !state.isImmersiveMode,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                val strings = com.linernotes.app.core.i18n.LocalStrings.current
                TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        val coverUrl = state.albumWithTracks?.album?.coverUrl
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .bouncyClickable(pressedScale = 0.92f) { isCoverViewerOpen = true }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = currentTrack?.title ?: (state.albumWithTracks?.album?.title ?: ""),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.albumWithTracks?.album?.artist ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    BouncyIconButton(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.padding(start = 8.dp).size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    // CD 蓝牙同步状态触钮
                    BouncyIconButton(
                        onClick = { viewModel.openCdSheet(true) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        if (state.cdConnectionState == CdConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (state.cdConnectionState == CdConnectionState.CONNECTED)
                                    Icons.Default.BluetoothConnected
                                else Icons.Default.Bluetooth,
                                contentDescription = strings.cdSyncTitle,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 沉浸模式全屏切换 (Zen Immersive Mode)
                    BouncyIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleImmersiveMode()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isImmersiveMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Immersive Mode",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Apple Music 风格三点更多按钮（集成模式切换、翻译、校对、设置）
                    Box {
                        BouncyIconButton(
                            onClick = { viewModel.setTranslateMenuOpen(!state.isTranslateMenuOpen) },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.padding(end = 8.dp).size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = state.isTranslateMenuOpen,
                            onDismissRequest = { viewModel.setTranslateMenuOpen(false) }
                        ) {
                            // 歌词显示模式切换
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            strings.modeBilingual,
                                            fontWeight = if (state.displayMode == LyricDisplayMode.BILINGUAL) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (state.displayMode == LyricDisplayMode.BILINGUAL) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDisplayMode(LyricDisplayMode.BILINGUAL)
                                    viewModel.setTranslateMenuOpen(false)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            strings.modeOriginal,
                                            fontWeight = if (state.displayMode == LyricDisplayMode.ORIGINAL_ONLY) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (state.displayMode == LyricDisplayMode.ORIGINAL_ONLY) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDisplayMode(LyricDisplayMode.ORIGINAL_ONLY)
                                    viewModel.setTranslateMenuOpen(false)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            strings.modeTranslated,
                                            fontWeight = if (state.displayMode == LyricDisplayMode.TRANSLATED_ONLY) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (state.displayMode == LyricDisplayMode.TRANSLATED_ONLY) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setDisplayMode(LyricDisplayMode.TRANSLATED_ONLY)
                                    viewModel.setTranslateMenuOpen(false)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(strings.fetchOfficialLyrics, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(strings.fetchOfficialLyricsDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.fetchOfficialLyricsCurrentTrack()
                                },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(strings.batchFetchOfficialAlbum, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(strings.batchFetchOfficialAlbumDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.batchFetchOfficialLyricsAlbum()
                                },
                                leadingIcon = { Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(strings.translateCurrentTrack, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.retranslateCurrentTrack()
                                },
                                leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(strings.batchTranslateAlbum, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(strings.batchTranslateAlbumDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.startBatchAlbumTranslation()
                                },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(strings.convertCurrentTrackToTraditional, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.convertCurrentTrackTranslation(toTraditional = true)
                                },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.convertCurrentTrackToSimplified, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.convertCurrentTrackTranslation(toTraditional = false)
                                },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 2.1 日语假名与罗马音注音切换
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("日文发音标注 (Furigana)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            when (state.furiganaMode) {
                                                FuriganaMode.OFF -> "当前: 关闭 · 点击开启假名"
                                                FuriganaMode.HIRAGANA -> "当前: 振假名 (Hiragana) · 点击切换罗马音"
                                                FuriganaMode.ROMAJI -> "当前: 罗马音 (Romaji) · 点击关闭"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.cycleFuriganaMode()
                                    viewModel.setTranslateMenuOpen(false)
                                },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            // 1.1 拟物 CD 光盘转盘模式切换
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Text("拟物 CD 光盘转盘", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("全息反射与物理 RPM 转速动效", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (state.isDiscViewExpanded) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.toggleDiscViewExpanded()
                                    viewModel.setTranslateMenuOpen(false)
                                },
                                leadingIcon = { Icon(Icons.Default.DiscFull, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                            )

                            // 1.2 官方原版内页画册与演职员表 (Digital Booklet & Credits)
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("实体画册 & 演职员表", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("翻阅 CD 扫描切页与官方 Credits", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.openBookletSheet(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            // 2.2 实体 CD 压盘版本库 (Discogs)
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("切换实体 CD 版本 (Discogs)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("日版SHM-CD/美版/SACD/首版压盘与内页", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.openDiscogsPicker(true)
                                },
                                leadingIcon = { Icon(Icons.Default.DiscFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            // 2.4 刷新 Genius 歌词典故与背景故事
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("重新检索 Genius 歌词典故", style = MaterialTheme.typography.bodyMedium)
                                        Text("重新抓取认证背景与内页故事", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.loadAnnotationsForCurrentTrack(forceRefresh = true)
                                },
                                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            // 2.5 导出标准 LRC 歌词
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("导出校准 LRC 歌词", style = MaterialTheme.typography.bodyMedium)
                                        Text("含时间轴偏移与双语对照", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    val lrcText = viewModel.getExportableLrc()
                                    val track = viewModel.getCurrentTrack()
                                    val fileName = "${track?.title ?: "lyrics"}.lrc"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                                        putExtra(Intent.EXTRA_TEXT, lrcText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "导出 LRC 歌词"))
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text(if (state.isTraditionalMode) "預熱全專典故與翻譯" else "预热全专典故与翻译", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.forcePrewarmAlbum()
                                },
                                leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFFD54F)) }
                            )

                            DropdownMenuItem(
                                text = { Text(strings.editLyricsAction, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.openEditSheet(true)
                                },
                                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.settingsTitle, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.setTranslateMenuOpen(false)
                                    viewModel.openSettings(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val coverUrl = state.albumWithTracks?.album?.coverUrl
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

            // 现代流光弥散动态呼吸背景 (Apple Music 风格双光斑流体呼吸场)
            AmbientGlowBackground(
                coverUrl = coverUrl,
                isDark = isDark
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            val strings = com.linernotes.app.core.i18n.LocalStrings.current

            Column(modifier = Modifier.fillMaxSize()) {
                // 正在后台翻译时的顶部常驻进度条
                if (isBatchTranslatingThisAlbum) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${strings.batchTranslatingBanner} [${batchState.currentTrackIndex}/${batchState.totalTracks}] ${batchState.currentTrackTitle ?: ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.cancelBatchAlbumTranslation() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        strings.cancel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val progress = if (batchState.totalTracks > 0) {
                                batchState.currentTrackIndex.toFloat() / batchState.totalTracks.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }
                    }
                }

                // 全专辑典故与翻译后台静默预热轻量胶囊指示条
                AnimatedVisibility(
                    visible = state.isAlbumPrewarming && state.prewarmProgress != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    state.prewarmProgress?.let { (ready, total) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.40f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFFFD54F)
                                    )
                                    Text(
                                        text = if (state.isTraditionalMode) 
                                            "正在後台靜默預熱全專典故與翻譯 · 已就緒 $ready/$total 首" 
                                            else "正在后台静默预热全专典故与翻译 · 已就绪 $ready/$total 首",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                Text(
                                    text = "0秒秒开",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD54F)
                                    )
                                )
                            }
                        }
                    }
                }

                // 顶部控制与功能胶囊区 (沉浸模式下自动平滑收起)
                AnimatedVisibility(
                    visible = !state.isImmersiveMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        // 1.1 拟物 CD 旋转光盘视窗 (可折叠展开)
                        AnimatedVisibility(
                    visible = state.isDiscViewExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val discRpm = (500f - (state.currentTrackIndex.toFloat() / maxOf(totalTracks, 1)) * 300f).toInt()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        RotatingCdDisc(
                            coverUrl = coverUrl,
                            isPlaying = state.isCompanionPlaying,
                            currentTrackIndex = state.currentTrackIndex,
                            totalTracks = totalTracks,
                            discSize = 220.dp,
                            onClick = { viewModel.toggleCompanionPlay() }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = if (state.isCompanionPlaying) "物理 CD 伺服转速: ~$discRpm RPM · 点击光盘启停" else "CD 伺服待机中 · 点击光盘启停",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Apple Music 风格横向功能胶囊快捷栏 (1-Tap Quick Action Capsule Bar)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 实体内页画册
                    item {
                        val pageCount = bookletPages.size
                        val bookletLabel = if (pageCount > 0) {
                            if (state.isTraditionalMode) "實體內頁 (${pageCount}P)" else "实体内页 (${pageCount}P)"
                        } else {
                            if (state.isTraditionalMode) "實體內頁" else "实体内页"
                        }
                        CapsuleFeatureChip(
                            icon = Icons.Default.Book,
                            label = bookletLabel,
                            isActive = pageCount > 0,
                            onClick = { viewModel.openBookletSheet(true) }
                        )
                    }

                    // 2. Genius 歌词典故与考据 (状态透明与一键重检)
                    item {
                        val hasAnnotations = state.lineAnnotations.isNotEmpty() || state.songStory != null
                        val annotCount = state.lineAnnotations.values.distinctBy {
                            if (it.id > 0L) it.id else "${it.lyricFragment}_${it.explanationText.take(20)}"
                        }.size + (if (state.songStory != null) 1 else 0)
                        val annotLabel = when {
                            state.isLoadingAnnotations -> if (state.isTraditionalMode) "典故檢索中..." else "典故检索中..."
                            hasAnnotations -> if (state.isTraditionalMode) "典故 (${annotCount})" else "典故 (${annotCount})"
                            else -> if (state.isTraditionalMode) "重檢典故" else "重检典故"
                        }
                        CapsuleFeatureChip(
                            icon = Icons.Default.MenuBook,
                            label = annotLabel,
                            isActive = hasAnnotations || state.isLoadingAnnotations,
                            tint = Color(0xFFFFD54F),
                            onClick = {
                                if (hasAnnotations) {
                                    if (state.songStory != null) {
                                        viewModel.toggleSongStoryExpanded()
                                    }
                                } else {
                                    viewModel.loadAnnotationsForCurrentTrack(forceRefresh = true)
                                }
                            }
                        )
                    }

                    // 3. 实体 CD 压盘版本库 (Discogs)
                    item {
                        val editionLabel = state.selectedDiscogsDetail?.let {
                            "CD · ${it.country ?: (if (state.isTraditionalMode) "首版" else "首版")}"
                        } ?: if (state.isTraditionalMode) "實體版本庫" else "实体版本库"
                        CapsuleFeatureChip(
                            icon = Icons.Default.Album,
                            label = editionLabel,
                            isActive = state.selectedDiscogsDetail != null,
                            onClick = { viewModel.openDiscogsPicker(true) }
                        )
                    }

                    // 4. 拟物 CD 旋转光盘视窗切换
                    item {
                        val cdLabel = if (state.isDiscViewExpanded) {
                            if (state.isTraditionalMode) "收起轉盤" else "收起转盘"
                        } else {
                            if (state.isTraditionalMode) "CD 轉盤" else "CD 转盘"
                        }
                        CapsuleFeatureChip(
                            icon = Icons.Default.DiscFull,
                            label = cdLabel,
                            isActive = state.isDiscViewExpanded,
                            onClick = { viewModel.toggleDiscViewExpanded() }
                        )
                    }

                    // 5. 歌词显示模式切换 (双语 / 原文 / 译文)
                    item {
                        val modeLabel = when (state.displayMode) {
                            LyricDisplayMode.BILINGUAL -> if (state.isTraditionalMode) "雙語對照" else "双语对照"
                            LyricDisplayMode.ORIGINAL_ONLY -> if (state.isTraditionalMode) "純原文" else "纯原文"
                            LyricDisplayMode.TRANSLATED_ONLY -> if (state.isTraditionalMode) "純譯文" else "纯译文"
                        }
                        CapsuleFeatureChip(
                            icon = Icons.Default.Translate,
                            label = modeLabel,
                            isActive = state.displayMode == LyricDisplayMode.BILINGUAL,
                            onClick = { viewModel.cycleDisplayMode() }
                        )
                    }

                    // 6. 日文注音 (假名 / 罗马音 / 关闭)
                    item {
                        val furiganaLabel = when (state.furiganaMode) {
                            FuriganaMode.OFF -> if (state.isTraditionalMode) "注音: 關" else "注音: 关"
                            FuriganaMode.HIRAGANA -> if (state.isTraditionalMode) "注音: 假名" else "注音: 假名"
                            FuriganaMode.ROMAJI -> if (state.isTraditionalMode) "注音: 羅馬音" else "注音: 罗马音"
                        }
                        CapsuleFeatureChip(
                            icon = Icons.Default.Language,
                            label = furiganaLabel,
                            isActive = state.furiganaMode != FuriganaMode.OFF,
                            onClick = { viewModel.cycleFuriganaMode() }
                        )
                    }

                    // 7. 繁简切换
                    item {
                        CapsuleFeatureChip(
                            icon = Icons.Default.Spellcheck,
                            label = if (state.isTraditionalMode) "繁體" else "简体",
                            isActive = state.isTraditionalMode,
                            onClick = { viewModel.toggleTraditionalMode() }
                        )
                    }

                    // 8. 时间轴校准
                    item {
                        val offsetLabel = if (state.lyricOffsetMs != 0L) {
                            "${if (state.lyricOffsetMs > 0) "+" else ""}${state.lyricOffsetMs}ms"
                        } else {
                            if (state.isTraditionalMode) "校準微調" else "校准微调"
                        }
                        CapsuleFeatureChip(
                            icon = Icons.Default.Tune,
                            label = offsetLabel,
                            isActive = state.showCalibrationBar || state.lyricOffsetMs != 0L,
                            onClick = { viewModel.toggleCalibrationBar() }
                        )
                    }
                    // 9. 沉浸模式切换 (Immersive Mode)
                    item {
                        val immersiveLabel = if (state.isImmersiveMode) {
                            if (state.isTraditionalMode) "退出沉浸" else "退出沉浸"
                        } else {
                            if (state.isTraditionalMode) "沉浸模式" else "沉浸模式"
                        }
                        CapsuleFeatureChip(
                            icon = if (state.isImmersiveMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            label = immersiveLabel,
                            isActive = state.isImmersiveMode,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleImmersiveMode()
                            }
                        )
                    }
                }
            }
        }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(state.currentTrackIndex, totalTracks, state.expandedAnnotationLineIndex, state.isImmersiveMode) {
                            var totalDragX = 0f
                            var startX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    startX = offset.x
                                    totalDragX = 0f
                                },
                                onDragEnd = {
                                    val width = size.width
                                    val edgeThreshold = 48.dp.toPx()
                                    if (startX <= edgeThreshold && totalDragX > 100f) {
                                        // 从左边缘向右侧滑：层级式返回 (优先收起行内卡片/退出沉浸，最后返回唱片架)
                                        handleBackAction()
                                    } else if (startX >= width - edgeThreshold && totalDragX < -100f) {
                                        // 从右边缘向左侧滑：层级式返回
                                        handleBackAction()
                                    } else if (totalDragX > 150f) {
                                        // 屏幕中央向右滑：上一曲
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.previousTrack()
                                    } else if (totalDragX < -150f) {
                                        // 屏幕中央向左滑：下一曲
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.nextTrack()
                                    }
                                    totalDragX = 0f
                                },
                                onDragCancel = { totalDragX = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount }
                            )
                        }
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleImmersiveMode()
                                },
                            contentPadding = PaddingValues(
                                start = 24.dp,
                                end = 24.dp,
                                top = if (state.isImmersiveMode) 32.dp else 16.dp,
                                bottom = if (state.isImmersiveMode) 100.dp else 180.dp
                            ),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (state.songStory != null) {
                                item {
                                    SongStoryOverviewCard(
                                        story = state.songStory,
                                        isExpanded = state.isSongStoryExpanded,
                                        onToggleExpand = { viewModel.toggleSongStoryExpanded() },
                                        isTranslating = state.isTranslatingSongStory,
                                        isTraditional = state.isTraditionalMode,
                                        onTranslate = { viewModel.translateSongStory(it) },
                                        modifier = Modifier.padding(bottom = 18.dp)
                                    )
                                }
                            } else if (state.isLoadingAnnotations && state.lineAnnotations.isEmpty()) {
                                // 1. 正在检索 Genius 典故时的动态微光提示栏 (彻底消除“等好久空白黑盒”问题)
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFFFFD54F).copy(alpha = 0.15f),
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "G",
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontWeight = FontWeight.Black,
                                                                color = Color(0xFFFFD54F)
                                                            )
                                                        )
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = if (state.isTraditionalMode) "正在檢索 Genius 歌詞典故與背景故事..." else "正在检索 Genius 歌词典故与背景故事...",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (state.isTraditionalMode) "跨洋同步全球樂迷考據解析中" else "跨洋同步全球乐迷考据解析中",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 1.8.dp,
                                                color = Color(0xFFFFD54F)
                                            )
                                        }
                                    }
                                }
                            } else if (!state.isLoadingAnnotations && state.lineAnnotations.isEmpty() && state.alignedLyrics.isNotEmpty()) {
                                val isFailed = state.annotationLoadState == AnnotationLoadState.FAILED
                                // 2. 检索完成但确实未收录典故 或 网络连接受阻时的状态栏 (支持一键重新检索)
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.MenuBook,
                                                    contentDescription = null,
                                                    tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = if (isFailed) {
                                                        if (state.isTraditionalMode) "網路連線受阻，未能同步 Genius 典故" else "网络连接受阻，未能同步 Genius 典故"
                                                    } else {
                                                        if (state.isTraditionalMode) "當前曲目在 Genius 暫無樂迷考據記錄" else "当前曲目在 Genius 暂无乐迷考据记录"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                                )
                                            }
                                            TextButton(
                                                onClick = { viewModel.loadAnnotationsForCurrentTrack(forceRefresh = true) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (state.isTraditionalMode) "重新檢索" else "重新检索",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (state.alignedLyrics.isEmpty()) {
                                item {
                                    Text(
                                        text = strings.noLyrics,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 64.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(state.alignedLyrics, key = { index, line -> "${line.lineNumber}_$index" }) { index, line ->
                                    val isActive = index == state.activeLineIndex
                                    val lineAnnotation = state.lineAnnotations[index]
                                    val isExpanded = state.expandedAnnotationLineIndex == index
                                    LyricLineItem(
                                        line = line,
                                        mode = state.displayMode,
                                        isActive = isActive,
                                        isCompanionPlaying = state.isCompanionPlaying,
                                        isImmersive = state.isImmersiveMode,
                                        furiganaMode = state.furiganaMode,
                                        annotation = lineAnnotation,
                                        isAnnotationExpanded = isExpanded,
                                        isTranslatingAnnotation = state.isTranslatingAnnotation,
                                        isTraditional = state.isTraditionalMode,
                                        onToggleAnnotation = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleInlineAnnotation(index)
                                        },
                                        onTranslateAnnotation = { viewModel.translateAnnotation(it, lineIndex = index) },
                                        onOpenFullAnnotation = {
                                            if (lineAnnotation != null) {
                                                viewModel.openAnnotation(lineAnnotation)
                                            }
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.onLyricLineClicked(index, line)
                                        }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 沉浸式模式下极简浮动退出胶囊 (Minimalist Immersive Pill)
            AnimatedVisibility(
                visible = state.isImmersiveMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setImmersiveMode(false)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BouncyIconButton(
                            onClick = {
                                viewModel.toggleCompanionPlay()
                            },
                            modifier = Modifier.size(28.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = if (state.isCompanionPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (state.isTraditionalMode) "沉浸模式 · 點擊退出" else "沉浸模式 · 点击退出",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit immersive",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Gemini 风格悬浮胶囊伴侣控制岛 (Floating Pill Island)
            AnimatedVisibility(
                visible = !state.isImmersiveMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FloatingCompanionCapsule(
                    trackNumber = currentTrack?.trackNumber ?: (state.currentTrackIndex + 1),
                    totalTracks = totalTracks,
                    trackTitle = currentTrack?.title?.takeIf { it.isNotBlank() } ?: if (state.cdConnectionState == CdConnectionState.CONNECTED && totalTracks > 0) "${strings.cdTrackFallback} ${String.format(java.util.Locale.getDefault(), "%02d", state.currentTrackIndex + 1)}" else "",
                    translatedTitle = currentTrack?.translatedTitle,
                    currentPosMs = state.currentPositionMs,
                    durationMs = state.trackDurationMs,
                    isPlaying = state.isCompanionPlaying,
                    hasPrevious = state.currentTrackIndex > 0,
                    hasNext = state.currentTrackIndex < totalTracks - 1,
                    showCalibration = state.showCalibrationBar,
                    currentOffsetMs = state.lyricOffsetMs,
                    cdConnectionState = state.cdConnectionState,
                    cdDeviceName = state.cdDeviceName,
                    onPrevious = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.previousTrack()
                    },
                    onNext = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.nextTrack()
                    },
                    onTogglePlay = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleCompanionPlay()
                    },
                    onSeek = { viewModel.seekCompanion(it) },
                    onAdjustOffset = { viewModel.adjustCompanionOffset(it) },
                    onResetOffset = { viewModel.resetCompanionOffset() },
                    onToggleCalibration = { viewModel.toggleCalibrationBar() },
                    onOpenCdSheet = { viewModel.openCdSheet(true) },
                    onOpenCdTracklist = { viewModel.openCdTracklist(true) }
                )
            }
        }
    }
}

    if (state.isEditingSheetOpen && currentTrack != null) {
        EditLyricSheet(
            track = currentTrack,
            onDismiss = { viewModel.openEditSheet(false) },
            onSave = { zhTitle, origLyrics, transLyrics ->
                viewModel.saveManualEdits(currentTrack.id, zhTitle, origLyrics, transLyrics)
            }
        )
    }

    if (state.isSettingsOpen) {
        SettingsDialog(
            aiPreferences = viewModel.aiPreferences,
            onDismiss = { viewModel.openSettings(false) },
            onSaved = {
                viewModel.openSettings(false)
            }
        )
    }

    if (state.isCdSheetOpen) {
        val pairedDevices = remember { viewModel.getPairedBluetoothDevices() }
        CdSyncSheet(
            connectionState = state.cdConnectionState,
            connectedDeviceName = state.cdDeviceName,
            pairedDevices = pairedDevices,
            onConnect = { device ->
                requestCdConnect(device)
            },
            onDisconnect = { viewModel.disconnectCdPlayer() },
            onDismiss = { viewModel.openCdSheet(false) },
            onOpenTracklist = {
                viewModel.openCdSheet(false)
                viewModel.openCdTracklist(true)
            }
        )
    }

    if (state.isCdTracklistOpen) {
        CdTracklistSheet(
            deviceName = state.cdDeviceName,
            connectionState = state.cdConnectionState,
            totalTracks = state.cdTotalTracks,
            currentTrackIndex = state.currentTrackIndex,
            cdPlaying = state.isCompanionPlaying,
            tracks = state.albumWithTracks?.tracks ?: emptyList(),
            album = state.albumWithTracks?.album,
            shelfAlbums = shelfAlbums,
            onSelectTrack = { trackIndex ->
                viewModel.playCdTrack(trackIndex)
                viewModel.openCdTracklist(false)
            },
            onTogglePlay = {
                viewModel.toggleCompanionPlay()
            },
            onSwitchAlbum = { targetAlbumId ->
                viewModel.switchAlbum(targetAlbumId)
            },
            onSaveMatchedAlbum = { matchedAlbum, matchedTracks ->
                viewModel.saveAndBindMatchedAlbum(matchedAlbum, matchedTracks)
            },
            onDismiss = { viewModel.openCdTracklist(false) }
        )
    }

    if (isCoverViewerOpen && !state.albumWithTracks?.album?.coverUrl.isNullOrBlank()) {
        val cover = state.albumWithTracks?.album?.coverUrl!!
        val discRpm = (500f - (state.currentTrackIndex.toFloat() / maxOf(totalTracks, 1)) * 300f).toInt()
        Dialog(onDismissRequest = { isCoverViewerOpen = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RotatingCdDisc(
                        coverUrl = cover,
                        isPlaying = state.isCompanionPlaying,
                        currentTrackIndex = state.currentTrackIndex,
                        totalTracks = totalTracks,
                        discSize = 250.dp,
                        onClick = { viewModel.toggleCompanionPlay() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.albumWithTracks?.album?.title ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.albumWithTracks?.album?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (state.isCompanionPlaying) "物理 CD 伺服转速: ~$discRpm RPM · 全息激光反射" else "CD 伺服待机中 · 点击底栏起播",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (state.isBookletSheetOpen) {
        DigitalBookletSheet(
            album = state.albumWithTracks?.album,
            tracks = state.albumWithTracks?.tracks ?: emptyList(),
            pages = bookletPages,
            onAddPages = { uris -> viewModel.addBookletPages(uris) },
            onDeletePage = { pageId -> viewModel.deleteBookletPage(pageId) },
            onOpenDiscogsPicker = {
                viewModel.openBookletSheet(false)
                viewModel.openDiscogsPicker(true)
            },
            onDismiss = { viewModel.openBookletSheet(false) }
        )
    }

    if (state.isDiscogsPickerOpen) {
        val initialQuery = state.albumWithTracks?.album?.let { "${it.title} ${it.artist}" } ?: ""
        DiscogsReleasePickerSheet(
            initialQuery = initialQuery,
            results = state.discogsResults,
            isLoading = state.isDiscogsLoading,
            currentAlbumCover = state.albumWithTracks?.album?.coverUrl,
            discogsToken = viewModel.aiPreferences.discogsToken,
            onSearch = { query -> viewModel.searchDiscogs(query) },
            onSelectRelease = { releaseId -> viewModel.applyDiscogsRelease(releaseId) },
            onViewDetail = { releaseId -> viewModel.viewDiscogsReleaseDetail(releaseId) },
            onDismiss = { viewModel.openDiscogsPicker(false) }
        )
    }

    if (state.isDiscogsDetailLoading) {
        Dialog(onDismissRequest = { viewModel.closeDiscogsReleaseDetail() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "正在拉取 Discogs 压盘详细档案...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (state.selectedDiscogsDetail != null) {
        DiscogsReleaseDetailSheet(
            detail = state.selectedDiscogsDetail!!,
            currentAlbumCover = state.albumWithTracks?.album?.coverUrl,
            discogsToken = viewModel.aiPreferences.discogsToken,
            onApplyRelease = { releaseId ->
                viewModel.applyDiscogsRelease(releaseId)
            },
            onDismiss = { viewModel.closeDiscogsReleaseDetail() }
        )
    }

    if (state.isAnnotationSheetOpen && state.selectedAnnotation != null) {
        val matchedTranslation = remember(state.selectedAnnotation, state.alignedLyrics) {
            val frag = state.selectedAnnotation?.lyricFragment?.trim()?.lowercase() ?: ""
            if (frag.isNotBlank()) {
                val matched = state.alignedLyrics.filter { line ->
                    val orig = line.original.trim().lowercase()
                    orig.isNotBlank() && (frag.contains(orig) || orig.contains(frag)) && line.translation.isNotBlank()
                }
                if (matched.isNotEmpty()) {
                    matched.joinToString("\n") { it.translation }
                } else null
            } else null
        }

        LyricAnnotationSheet(
            annotation = state.selectedAnnotation,
            isTranslating = state.isTranslatingAnnotation,
            isTraditional = state.isTraditionalMode,
            lyricTranslation = matchedTranslation,
            onTranslate = { viewModel.translateAnnotation(it) },
            onDismiss = { viewModel.dismissAnnotationSheet() }
        )
    }
}

/**
 * Gemini 风格悬浮胶囊控制岛 (Floating Pill Island)
 */
@Composable
private fun FloatingCompanionCapsule(
    trackNumber: Int,
    totalTracks: Int,
    trackTitle: String,
    translatedTitle: String?,
    currentPosMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    showCalibration: Boolean,
    currentOffsetMs: Long = 0L,
    cdConnectionState: CdConnectionState = CdConnectionState.DISCONNECTED,
    cdDeviceName: String? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit = {},
    onToggleCalibration: () -> Unit,
    onOpenCdSheet: () -> Unit = {},
    onOpenCdTracklist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // CD 蓝牙同步状态浮动胶囊 (Gemini Pill)
        Surface(
            shape = CircleShape,
            color = when (cdConnectionState) {
                CdConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
                CdConnectionState.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                CdConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            },
            border = BorderStroke(
                1.dp,
                if (cdConnectionState == CdConnectionState.CONNECTED) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .bouncyClickable(pressedScale = 0.94f) { onOpenCdSheet() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                if (cdConnectionState == CdConnectionState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (cdConnectionState) {
                                    CdConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                    CdConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
                Text(
                    text = when (cdConnectionState) {
                        CdConnectionState.CONNECTED -> "已同步 CD: ${cdDeviceName ?: "山灵 EC Mini"}"
                        CdConnectionState.CONNECTING -> strings.cdSyncConnecting
                        CdConnectionState.FAILED -> "CD 连接失败 · 点击重试"
                        CdConnectionState.DISCONNECTED -> "未连接 CD 机 · 点击连接"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (cdConnectionState == CdConnectionState.CONNECTED) FontWeight.Bold else FontWeight.Medium,
                    color = when (cdConnectionState) {
                        CdConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        CdConnectionState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // 微调对齐胶囊托盘 (毫秒级高精度校准)
        AnimatedVisibility(
            visible = showCalibration,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .shadow(10.dp, RoundedCornerShape(20.dp))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "时间轴微调: ${if (currentOffsetMs >= 0) "+$currentOffsetMs" else "$currentOffsetMs"} ms",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (currentOffsetMs != 0L) {
                            Text(
                                text = "· 重置 (0ms)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.bouncyClickable { onResetOffset() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BouncyTonalButton(
                            onClick = { onAdjustOffset(-500L) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("-500ms", style = MaterialTheme.typography.labelSmall)
                        }
                        BouncyTonalButton(
                            onClick = { onAdjustOffset(-100L) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("-100ms", style = MaterialTheme.typography.labelSmall)
                        }
                        BouncyTonalButton(
                            onClick = { onAdjustOffset(100L) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+100ms", style = MaterialTheme.typography.labelSmall)
                        }
                        BouncyTonalButton(
                            onClick = { onAdjustOffset(500L) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+500ms", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 主胶囊药丸容器 (Apple Music / Gemini Pill Island)
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 顶部平滑时间进度调节 (Draggable Slider & Timestamps)
                var isDragging by remember { mutableStateOf(false) }
                var dragPositionMs by remember { mutableStateOf(0L) }

                val displayPosMs = if (isDragging) dragPositionMs else currentPosMs
                val maxDuration = durationMs.coerceAtLeast(1L)
                val sliderPos = (displayPosMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = formatTime(displayPosMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(38.dp)
                    )
                    Slider(
                        value = sliderPos,
                        onValueChange = { frac ->
                            isDragging = true
                            dragPositionMs = (frac * maxDuration).toLong()
                        },
                        onValueChangeFinished = {
                            onSeek(dragPositionMs)
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 控制按钮行 (Modern Player Island Layout)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 左侧：曲名与音轨胶囊（可点击快速展开曲目列表）
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .bouncyClickable(pressedScale = 0.96f) { onOpenCdTracklist() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            if (cdConnectionState == CdConnectionState.CONNECTED) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(end = 5.dp)
                                ) {
                                    Text(
                                        text = "CD",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (trackTitle.isNotBlank()) "$trackNumber. $trackTitle" else "Track $trackNumber",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!translatedTitle.isNullOrBlank()) {
                            Text(
                                text = translatedTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // 右侧：媒体控制与快捷操作坞 (Transport Dock)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 上一曲
                        BouncyIconButton(
                            onClick = onPrevious,
                            enabled = hasPrevious,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = strings.prevTrack,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // 核心高亮播放/暂停大圆纽 (Hero Button)
                        val playInteractionSource = remember { MutableInteractionSource() }
                        val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                        val heroScale by animateFloatAsState(
                            targetValue = if (isPlayPressed) 0.88f else if (isPlaying) 1.04f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "heroScale"
                        )
                        val heroAlpha by animateFloatAsState(
                            targetValue = if (isPlayPressed) 0.85f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "heroAlpha"
                        )

                        Surface(
                            onClick = onTogglePlay,
                            interactionSource = playInteractionSource,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(44.dp)
                                .scale(heroScale)
                                .graphicsLayer { alpha = heroAlpha }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleIn(initialScale = 0.8f))
                                            .togetherWith(
                                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                        scaleOut(targetScale = 0.8f)
                                            )
                                    },
                                    label = "playPause"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) strings.cdCompanionPause else strings.cdCompanionPlay,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                            }
                        }

                        // 下一曲
                        BouncyIconButton(
                            onClick = onNext,
                            enabled = hasNext,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = strings.nextTrack,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // CD 实时曲目清单快捷按钮 (QueueMusic)
                        BouncyIconButton(
                            onClick = onOpenCdTracklist,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = strings.cdTracklistTitle,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // 校准微调芯片（圆形）
                        BouncyIconButton(
                            onClick = onToggleCalibration,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showCalibration) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                contentColor = if (showCalibration) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tune",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}


/**
 * 具有毫秒级时间轴聚焦动效的歌词行 (Tap-to-seek, smooth animated scale/alpha)
 */
@Composable
private fun LyricLineItem(
    line: BilingualLyricLine,
    mode: LyricDisplayMode,
    isActive: Boolean,
    isCompanionPlaying: Boolean,
    isImmersive: Boolean = false,
    furiganaMode: FuriganaMode = FuriganaMode.OFF,
    annotation: com.linernotes.app.data.local.entity.LyricAnnotationEntity? = null,
    isAnnotationExpanded: Boolean = false,
    isTranslatingAnnotation: Boolean = false,
    isTraditional: Boolean = false,
    onToggleAnnotation: () -> Unit = {},
    onTranslateAnnotation: (com.linernotes.app.data.local.entity.LyricAnnotationEntity) -> Unit = {},
    onOpenFullAnnotation: () -> Unit = {},
    onClick: () -> Unit
) {
    if (line.isStanzaBreak) {
        Spacer(modifier = Modifier.height(28.dp))
        return
    }

    val hasAnnotation = annotation != null

    val lineInteractionSource = remember { MutableInteractionSource() }
    val isLinePressed by lineInteractionSource.collectIsPressedAsState()

    val alphaAnim by animateFloatAsState(
        targetValue = if (isLinePressed) 0.65f else if (isActive) 1.0f else if (isCompanionPlaying) 0.38f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricAlpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isLinePressed) 0.97f else if (isActive) (if (isImmersive) 1.04f else 1.025f) else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "lyricScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = if (mode == LyricDisplayMode.BILINGUAL) (if (isImmersive) 12.dp else 10.dp) else (if (isImmersive) 8.dp else 6.dp))
            .clickable(
                interactionSource = lineInteractionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                alpha = alphaAnim
                scaleX = scaleAnim
                scaleY = scaleAnim
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        horizontalAlignment = Alignment.Start
    ) {
        if (hasAnnotation && annotation != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isAnnotationExpanded) Color(0xFFFFD54F).copy(alpha = 0.22f) else Color(0xFFFFD54F).copy(alpha = 0.12f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isAnnotationExpanded) Color(0xFFFFD54F).copy(alpha = 0.65f) else Color(0xFFFFD54F).copy(alpha = 0.30f)
                ),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleAnnotation() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (annotation.isVerified) Icons.Default.Verified else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F).copy(alpha = 0.95f),
                        modifier = Modifier.size(11.dp)
                    )
                    val isPassage = remember(annotation.lyricFragment) {
                        annotation.lyricFragment.lines().count { it.isNotBlank() } > 1
                    }
                    Text(
                        text = if (annotation.isVerified) {
                            if (isPassage) {
                                if (isTraditional) "認證典故 (段落)" else "认证典故 (段落)"
                            } else {
                                if (isTraditional) "認證典故" else "认证典故"
                            }
                        } else {
                            if (isPassage) {
                                if (isTraditional) "典故 (段落)" else "典故 (段落)"
                            } else {
                                if (isTraditional) "典故" else "典故"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp,
                            color = Color(0xFFFFD54F)
                        )
                    )
                    Icon(
                        imageVector = if (isAnnotationExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F).copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        val displayTranslation = remember(line.translation, isTraditional) {
            if (isTraditional) ChineseConverter.toTraditional(line.translation) else line.translation
        }

        // 播放焦点行高亮流光指示器 + 歌词主文本
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AnimatedVisibility(
                visible = isActive && isCompanionPlaying,
                enter = expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp, top = 6.dp)
                        .width(4.dp)
                        .height(if (isImmersive) 32.dp else 26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color(0xFFFFD54F)
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                when (mode) {
                    LyricDisplayMode.BILINGUAL -> {
                        if (line.original.isNotBlank()) {
                            OriginalLyricText(
                                text = line.original,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = if (isImmersive) 32.sp else 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = if (isImmersive) 42.sp else 36.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                furiganaMode = furiganaMode,
                                isActive = isActive
                            )
                        }
                        if (displayTranslation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(if (isImmersive) 6.dp else 5.dp))
                            Text(
                                text = displayTranslation,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = if (isImmersive) 20.sp else 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = if (isImmersive) 28.sp else 24.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    LyricDisplayMode.ORIGINAL_ONLY -> {
                        if (line.original.isNotBlank()) {
                            OriginalLyricText(
                                text = line.original,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = if (isImmersive) 34.sp else 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = if (isImmersive) 44.sp else 38.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                furiganaMode = furiganaMode,
                                isActive = isActive
                            )
                        }
                    }

                    LyricDisplayMode.TRANSLATED_ONLY -> {
                        if (displayTranslation.isNotBlank()) {
                            Text(
                                text = displayTranslation,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = if (isImmersive) 28.sp else 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = if (isImmersive) 36.sp else 32.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }

        if (hasAnnotation && annotation != null) {
            AnimatedVisibility(
                visible = isAnnotationExpanded,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                InlineLyricAnnotationCard(
                    annotation = annotation,
                    isTranslating = isTranslatingAnnotation,
                    isTraditional = isTraditional,
                    onTranslate = onTranslateAnnotation,
                    onOpenFullSheet = onOpenFullAnnotation,
                    onCollapse = onToggleAnnotation,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OriginalLyricText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    furiganaMode: FuriganaMode,
    isActive: Boolean
) {
    if (furiganaMode != FuriganaMode.OFF && FuriganaEngine.isJapanese(text)) {
        val segments = remember(text) { FuriganaEngine.segmentText(text) }
        FuriganaText(
            segments = segments,
            mode = furiganaMode,
            baseTextStyle = style,
            rubyTextStyle = style.copy(fontSize = (style.fontSize.value * 0.48f).sp, fontWeight = FontWeight.Normal),
            isActive = isActive,
            activeColor = color,
            inactiveColor = color
        )
    } else {
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun CapsuleFeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val activeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val inactiveBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val activeBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val inactiveBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val contentColor = tint ?: if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = if (isActive) activeBg else inactiveBg,
        border = BorderStroke(0.5.dp, if (isActive) activeBorder else inactiveBorder),
        modifier = modifier
            .height(32.dp)
            .bouncyClickable(
                pressedScale = 0.93f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}



