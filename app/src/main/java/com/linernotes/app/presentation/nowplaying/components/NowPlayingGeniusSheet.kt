package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.presentation.booklet.components.BilingualContentView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingGeniusSheet(
    annotation: LyricAnnotationEntity?,
    isTraditional: Boolean = false,
    onDismiss: () -> Unit
) {
    if (annotation == null) return

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
            // 顶部认证条与关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFFFC107).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (annotation.isVerified) "Genius 官方认证" else "Genius 乐评典故",
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 被引用的歌词原句卡片
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "❝ ${annotation.lyricFragment} ❞",
                        color = Color(0xFFFFE082),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                    if (!annotation.lyricTranslation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "❝ ${annotation.lyricTranslation} ❞",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "典故与背景深度考据",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 精准句对句/段对段双语对照视图 (中文在上，较小字号英文在下，支持中英对照/纯中文/纯英文平滑切换)
            BilingualContentView(
                originalText = annotation.explanationText,
                translatedText = annotation.explanationTranslation,
                isTraditional = isTraditional,
                isDark = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 底部贡献者信息与点赞
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!annotation.authorAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = annotation.authorAvatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFC107).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = annotation.authorName ?: "Genius Contributor",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                if (annotation.votesTotal > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${annotation.votesTotal} 赞同",
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
