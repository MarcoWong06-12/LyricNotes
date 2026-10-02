package com.linernotes.app.presentation.booklet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.MetadataService
import com.linernotes.app.data.remote.OnlineAlbumInfo
import com.linernotes.app.presentation.common.BouncyButton
import com.linernotes.app.presentation.common.BouncyIconButton
import com.linernotes.app.presentation.common.bouncyClickable
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdTracklistSheet(
    deviceName: String?,
    connectionState: CdConnectionState,
    totalTracks: Int,
    currentTrackIndex: Int,
    cdPlaying: Boolean,
    tracks: List<TrackEntity>,
    album: AlbumEntity?,
    shelfAlbums: List<AlbumEntity>,
    onSelectTrack: (trackIndex: Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSwitchAlbum: (albumId: String) -> Unit,
    onSaveMatchedAlbum: (AlbumEntity, List<TrackEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var isSearchSheetOpen by remember { mutableStateOf(false) }

    // 计算展示的总音轨数：以 CD 上报的音轨数为准，若未上报则以本地曲目数为准
    val effectiveCount = if (totalTracks > 0) totalTracks else tracks.size.coerceAtLeast(1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 1. 标题与状态栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = strings.cdTracklistTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${deviceName ?: "山灵 EC Mini"} · 共 $effectiveCount 首曲目",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // 识别/匹配唱片按钮 (Gemini Pill Button)
                BouncyButton(
                    onClick = { isSearchSheetOpen = true },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.matchCdAlbumAction,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. 当前匹配的唱片信息条 (若有)
            if (album != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        if (album.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(album.coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 若曲目数不符，显示提醒
                        if (totalTracks > 0 && tracks.isNotEmpty() && totalTracks != tracks.size) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "曲目数不符 (${tracks.size}/$totalTracks)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                // 未匹配唱片时的提示卡片
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "实体 CD 通常无曲名信息",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "点击右上角「识别与匹配此 CD」，可自动检索匹配完整歌曲名与双语歌词。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. 歌曲列表 (即点即切)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(effectiveCount) { index ->
                    val trackNumber = index + 1
                    val track = tracks.getOrNull(index)
                    val isCurrent = index == currentTrackIndex

                    val titleText = track?.title?.takeIf { it.isNotBlank() } ?: "${strings.cdTrackFallback} ${String.format(Locale.getDefault(), "%02d", trackNumber)}"
                    val transText = track?.translatedTitle?.takeIf { !it.isNullOrBlank() }
                    val durationMs = track?.durationMs
                    val durationText = if (durationMs != null && durationMs > 0L) {
                        formatDuration(durationMs)
                    } else null

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable {
                                onSelectTrack(index)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            // 序号徽标
                            Surface(
                                shape = CircleShape,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d", trackNumber),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 曲目名与译文
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!transText.isNullOrBlank()) {
                                    Text(
                                        text = transText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (durationText != null) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            // 状态图标：正在播放显示双竖线 Pause，暂停显示三角 PlayArrow。支持独立点击控制播放/暂停
                            if (isCurrent) {
                                IconButton(
                                    onClick = onTogglePlay,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (cdPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (cdPlaying) strings.cdCompanionPause else strings.cdCompanionPlay,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. 在线检索并匹配 CD 唱片对话框
    if (isSearchSheetOpen) {
        CdMatchOnlineSearchDialog(
            defaultQuery = album?.title ?: album?.artist ?: "",
            expectedTrackCount = effectiveCount,
            shelfAlbums = shelfAlbums,
            onSelectShelfAlbum = { shelfAlbum ->
                onSwitchAlbum(shelfAlbum.id)
                isSearchSheetOpen = false
            },
            onSaveOnlineAlbum = { newAlbum, newTracks ->
                onSaveMatchedAlbum(newAlbum, newTracks)
                isSearchSheetOpen = false
            },
            onDismiss = { isSearchSheetOpen = false }
        )
    }
}

/**
 * 在线搜索与本地唱片架匹配弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CdMatchOnlineSearchDialog(
    defaultQuery: String,
    expectedTrackCount: Int,
    shelfAlbums: List<AlbumEntity>,
    onSelectShelfAlbum: (AlbumEntity) -> Unit,
    onSaveOnlineAlbum: (AlbumEntity, List<TrackEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val strings = LocalStrings.current
    var query by remember { mutableStateOf(defaultQuery) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<OnlineAlbumInfo>>(emptyList()) }
    var isImporting by remember { mutableStateOf(false) }

    // 本地曲目数匹配的候选唱片
    val matchingShelfAlbums = remember(expectedTrackCount, shelfAlbums) {
        shelfAlbums.filter { true } // 可由用户选择
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("匹配 CD 专辑信息", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                Text(
                    text = "输入专辑名或歌手，系统将在线拉取曲目名称与双语歌词；标有「✨ 曲目数匹配」的即为此 CD 对应版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 搜索框
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索唱片名、歌手 (如: Nas / 周杰伦)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (query.isNotBlank() && !isSearching) {
                                isSearching = true
                                coroutineScope.launch {
                                    val results = MetadataService.searchAlbums(query)
                                    searchResults = results
                                    isSearching = false
                                }
                            }
                        },
                        enabled = query.isNotBlank() && !isSearching
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("搜索")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isImporting) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("正在导入专辑信息并抓取双语歌词...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(searchResults) { _, item ->
                            val isCountMatch = item.trackCount == expectedTrackCount

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCountMatch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (isCountMatch) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        isImporting = true
                                        coroutineScope.launch {
                                            try {
                                                val albumId = java.util.UUID.randomUUID().toString()
                                                val tracks = MetadataService.fetchTracksWithLyrics(
                                                    collectionId = item.collectionId,
                                                    source = item.source,
                                                    albumId = albumId,
                                                    artistName = item.artist,
                                                    albumTitle = item.title
                                                )
                                                val albumEntity = AlbumEntity(
                                                    id = albumId,
                                                    title = item.title,
                                                    artist = item.artist,
                                                    releaseYear = item.releaseYear,
                                                    coverUrl = item.coverUrl,
                                                    purchaseDate = System.currentTimeMillis()
                                                )
                                                onSaveOnlineAlbum(albumEntity, tracks)
                                            } catch (e: Exception) {
                                                isImporting = false
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    if (item.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(item.coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCountMatch) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "✨ 曲目数匹配 (${item.trackCount}首)",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${item.artist} · ${item.releaseYear} · 共 ${item.trackCount} 首",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
