package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.presentation.booklet.components.BilingualContentView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSongStorySheet(
    story: SongStoryEntity?,
    isTraditional: Boolean = false,
    onDismiss: () -> Unit
) {
    if (story == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141418),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.3f)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BEHIND THE SONG",
                        color = Color(0xFF1DB954),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = story.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = story.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            if (!story.headerImageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                AsyncImage(
                    model = story.headerImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            if (!story.releaseDate.isNullOrBlank() || !story.producerCredits.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    story.releaseDate?.takeIf { it.isNotBlank() }?.let { date ->
                        Column {
                            Text("发行日期", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            Text(date, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    story.producerCredits?.takeIf { it.isNotBlank() }?.let { prod ->
                        Column {
                            Text("制作人", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            Text(prod, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "全曲背景故事与创作考据",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 精准句对句/段对段双语对照视图 (中文在上，较小字号英文在下，支持中英对照/纯中文/纯英文平滑切换)
            BilingualContentView(
                originalText = story.descriptionPlain,
                translatedText = story.descriptionTranslation,
                isTraditional = isTraditional,
                isDark = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
