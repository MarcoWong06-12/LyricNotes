package com.linernotes.app.presentation.shelf.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.MetadataService
import com.linernotes.app.data.remote.OnlineAlbumInfo
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCdBottomSheet(
    onDismiss: () -> Unit,
    onSaveAlbum: (AlbumEntity, List<TrackEntity>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val strings = com.linernotes.app.core.i18n.LocalStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<OnlineAlbumInfo>>(emptyList()) }
    var selectedCandidate by remember { mutableStateOf<OnlineAlbumInfo?>(null) }
    var matchedCollectionId by remember { mutableLongStateOf(0L) }
    var selectedSource by remember { mutableStateOf("iTunes") }

    var title by remember { mutableStateOf("") }
    var translatedTitle by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var releaseYear by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // 系统相册图片选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUrl = uri.toString()
        }
    }

    val performSearch: () -> Unit = {
        if (searchQuery.isNotBlank() && !isSearching) {
            isSearching = true
            searchError = null
            searchResults = emptyList()
            selectedCandidate = null
            coroutineScope.launch {
                val results = MetadataService.searchAlbums(searchQuery)
                isSearching = false
                if (results.isNotEmpty()) {
                    searchResults = results
                    val first = results[0]
                    selectedCandidate = first
                    title = first.title
                    artist = first.artist
                    releaseYear = first.releaseYear
                    coverUrl = first.coverUrl
                    matchedCollectionId = first.collectionId
                    selectedSource = first.source
                    if (first.title.equals("Abbey Road", ignoreCase = true)) {
                        translatedTitle = "修道院之路"
                    }
                } else {
                    searchError = strings.searchNotFound
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = strings.addAlbumTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(strings.tabOnlineSearch) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(strings.tabManualEntry) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(strings.searchAlbumLabel) },
                    placeholder = { Text(strings.searchAlbumPlaceholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    trailingIcon = {
                        IconButton(
                            onClick = performSearch,
                            enabled = searchQuery.isNotBlank() && !isSearching
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.searchingOnline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (searchError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = searchError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 搜索候选结果列表（可点击挑选）
                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = strings.searchResultsTitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        searchResults.forEach { candidate ->
                            val isSelected = selectedCandidate?.collectionId == candidate.collectionId &&
                                    selectedCandidate?.source == candidate.source

                            Card(
                                onClick = {
                                    selectedCandidate = candidate
                                    title = candidate.title
                                    artist = candidate.artist
                                    releaseYear = candidate.releaseYear
                                    coverUrl = candidate.coverUrl
                                    matchedCollectionId = candidate.collectionId
                                    selectedSource = candidate.source
                                    if (candidate.title.equals("Abbey Road", ignoreCase = true)) {
                                        translatedTitle = "修道院之路"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    }
                                ),
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(candidate.coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = candidate.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = candidate.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = if (candidate.source == "NetEase") {
                                                    Color(0xFFE60026).copy(alpha = 0.12f)
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (candidate.source == "NetEase") strings.sourceBadgeNetease else strings.sourceBadgeItunes,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                    color = if (candidate.source == "NetEase") Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = candidate.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${candidate.releaseYear} · ${candidate.trackCount} ${strings.searchTracksCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 封面预览与相册选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "封面预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (coverUrl.isBlank()) strings.pickFromGallery else strings.albumCover)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.albumTitleLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = translatedTitle,
                onValueChange = { translatedTitle = it },
                label = { Text(strings.albumTranslatedTitleLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(strings.artistLabel) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = releaseYear,
                    onValueChange = { releaseYear = it },
                    label = { Text(strings.releaseYearLabel) },
                    modifier = Modifier.width(110.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = coverUrl,
                onValueChange = { coverUrl = it },
                label = { Text("Cover URL / Local Path") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Barcode / Catalog No.") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Remarks") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        isSearching = true
                        val albumId = UUID.randomUUID().toString()
                        val album = AlbumEntity(
                            id = albumId,
                            title = title,
                            translatedTitle = translatedTitle.ifBlank { null },
                            artist = artist,
                            releaseYear = releaseYear.ifBlank { "N/A" },
                            coverUrl = coverUrl,
                            barcode = barcode.ifBlank { null },
                            purchaseDate = System.currentTimeMillis(),
                            notes = notes.ifBlank { null }
                        )

                        coroutineScope.launch {
                            val tracks = if (matchedCollectionId > 0L) {
                                // 抓取真实完整曲目单及歌词库中的全篇歌词（依据所选网易云或 Apple Music 精准下载）
                                MetadataService.fetchTracksWithLyrics(
                                    collectionId = matchedCollectionId,
                                    source = selectedSource,
                                    albumId = albumId,
                                    artistName = artist,
                                    albumTitle = title
                                )
                            } else {
                                // 手动录入时保底创建首轨
                                listOf(
                                    TrackEntity(
                                        albumId = albumId,
                                        trackNumber = 1,
                                        title = title,
                                        translatedTitle = translatedTitle.ifBlank { null },
                                        originalLyrics = "",
                                        translatedLyrics = null
                                    )
                                )
                            }
                            isSearching = false
                            onSaveAlbum(album, tracks)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && artist.isNotBlank() && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.searchingOnline)
                } else {
                    Text(strings.saveAlbumBtn)
                }
            }
        }
    }
}
