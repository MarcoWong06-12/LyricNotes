package com.linernotes.app.presentation.booklet.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.entity.TrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLyricSheet(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onSave: (translatedTitle: String?, originalLyrics: String?, translatedLyrics: String?) -> Unit
) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current

    var zhTitle by remember { mutableStateOf(track.translatedTitle ?: "") }
    var origLyrics by remember { mutableStateOf(track.originalLyrics ?: "") }
    var transLyrics by remember { mutableStateOf(track.translatedLyrics ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = strings.editSheetTitle,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = zhTitle,
                onValueChange = { zhTitle = it },
                label = { Text(strings.editTrackTranslatedTitleLabel) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = origLyrics,
                onValueChange = { origLyrics = it },
                label = { Text(strings.editTrackOriginalLyricsLabel) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        zhTitle = ChineseConverter.toTraditional(zhTitle)
                        transLyrics = ChineseConverter.toTraditional(transLyrics)
                    },
                    label = { Text(strings.convertToTraditional) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = {
                        zhTitle = ChineseConverter.toSimplified(zhTitle)
                        transLyrics = ChineseConverter.toSimplified(transLyrics)
                    },
                    label = { Text(strings.convertToSimplified) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = transLyrics,
                onValueChange = { transLyrics = it },
                label = { Text(strings.editTrackTranslatedLyricsLabel) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(strings.cancel)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(zhTitle.ifBlank { null }, origLyrics, transLyrics)
                    }
                ) {
                    Text(strings.saveChanges)
                }
            }
        }
    }
}
