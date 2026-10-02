package com.linernotes.app.presentation.shelf.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.linernotes.app.data.local.entity.AlbumEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CdCard(
    album: AlbumEntity,
    onClick: () -> Unit,
    onViewBooklet: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp)
    ) {
        val strings = com.linernotes.app.core.i18n.LocalStrings.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(3.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawWithContent {
                    drawContent()

                    // 左侧透明塑料铰链脊光泽
                    val spineWidth = size.width * 0.065f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.30f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = spineWidth * 1.5f
                        ),
                        topLeft = Offset.Zero,
                        size = Size(spineWidth * 1.5f, size.height)
                    )

                    drawLine(
                        color = Color.Black.copy(alpha = 0.4f),
                        start = Offset(spineWidth, 0f),
                        end = Offset(spineWidth, size.height),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 45 度斜切塑料反光
                    drawRect(
                        brush = Brush.linearGradient(
                            0.0f to Color.White.copy(alpha = 0.12f),
                            0.15f to Color.White.copy(alpha = 0.04f),
                            0.40f to Color.Transparent,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )
                    )

                    // 外圈亚克力微高光描边
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.20f)
                            )
                        ),
                        topLeft = Offset.Zero,
                        size = size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx())
                    )
                }
                .combinedClickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { isMenuExpanded = true }
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 实体唱片介质角标 (CD / SACD / VINYL / CASSETTE)
            val mediaType = (album.mediaType.ifBlank { "CD" }).uppercase()
            Surface(
                shape = RoundedCornerShape(bottomStart = 6.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(
                    0.5.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
                    )
                ),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = mediaType,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(strings.viewBooklet) },
                    onClick = {
                        isMenuExpanded = false
                        onViewBooklet()
                    }
                )
                DropdownMenuItem(
                    text = { Text(strings.removeFromShelf, color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        isMenuExpanded = false
                        onRemove()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${album.artist} • ${album.releaseYear}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
