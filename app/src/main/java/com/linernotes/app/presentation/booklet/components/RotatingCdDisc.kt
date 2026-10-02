package com.linernotes.app.presentation.booklet.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linernotes.app.presentation.common.bouncyClickable
import com.linernotes.app.presentation.theme.VaultBlack
import com.linernotes.app.presentation.theme.VaultSurfaceVariant
import kotlinx.coroutines.isActive

@Composable
fun RotatingCdDisc(
    modifier: Modifier = Modifier,
    coverUrl: String?,
    isPlaying: Boolean,
    currentTrackIndex: Int = 0,
    totalTracks: Int = 1,
    discSize: Dp = 280.dp,
    onClick: (() -> Unit)? = null
) {
    // 1. Calculate RPM and degrees/ms
    val rpm = 500f - (currentTrackIndex.toFloat() / maxOf(totalTracks, 1)) * 300f
    val degreesPerMs = (rpm * 360f) / 60000f

    // Track rotation angle manually to allow spring pausing
    var accumulatedAngle by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(isPlaying, degreesPerMs) {
        if (isPlaying) {
            var lastTime = withFrameMillis { it }
            while (isActive) {
                val currentTime = withFrameMillis { it }
                val deltaMs = (currentTime - lastTime).toFloat()
                lastTime = currentTime
                accumulatedAngle += deltaMs * degreesPerMs
            }
        }
    }

    // 2. Smooth animation for spring dampening on pause
    val animatedAngle by animateFloatAsState(
        targetValue = accumulatedAngle,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "discRotation"
    )

    // Using rememberInfiniteTransition for continuous gradient animation 
    // as per instructions to incorporate it with LinearEasing.
    val infiniteTransition = rememberInfiniteTransition(label = "sheenTransition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val rainbowColors = remember {
        listOf(
            Color.Transparent,
            Color.Cyan.copy(alpha = 0.2f),
            Color.Magenta.copy(alpha = 0.15f),
            Color.Yellow.copy(alpha = 0.2f),
            Color.Cyan.copy(alpha = 0.15f),
            Color.Transparent
        )
    }

    val lightBeamColors = remember {
        listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.3f),
            Color.White.copy(alpha = 0.1f),
            Color.Transparent
        )
    }

    val clickModifier = if (onClick != null) {
        Modifier.bouncyClickable(
            pressedScale = 0.95f,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(discSize)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = animatedAngle
                    alpha = 0.7f
                }
                .clip(CircleShape)
                .drawWithContent {
                    drawContent()
                    
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    val innerRadius = radius * 0.15f

                    // Rainbow Sheen (Rotates OPPOSITE to disc rotation)
                    // The canvas is already rotated by `animatedAngle` via graphicsLayer.
                    // To make sheen rotate opposite visually, net rotation = -animatedAngle.
                    // So relative rotation = -animatedAngle * 2
                    rotate(degrees = -animatedAngle * 2f, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = rainbowColors,
                                center = center
                            ),
                            radius = radius,
                            center = center
                        )
                    }

                    // Light beam (Sweeps across at half speed)
                    // Net rotation = animatedAngle * 0.5f + continuous sweep
                    // Relative rotation = (animatedAngle * 0.5f + sweepAngle) - animatedAngle
                    rotate(degrees = sweepAngle - (animatedAngle * 0.5f), pivot = center) {
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = lightBeamColors,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            ),
                            radius = radius,
                            center = center
                        )
                    }

                    // Static overlays (Counter-rotate to keep them fixed visually)
                    rotate(degrees = -animatedAngle, pivot = center) {
                        // Outer ring edge (Silver/chrome)
                        drawCircle(
                            color = Color(0xFFE2E2E6), // JewelCaseGlow
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Specular highlight on outer ring
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.8f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.3f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            ),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Data tracks (Subtle concentric grooves)
                    val trackCount = 12
                    for (i in 0 until trackCount) {
                        val trackRadius = innerRadius + (radius - innerRadius) * (i + 1) / (trackCount + 1)
                        drawCircle(
                            color = VaultBlack.copy(alpha = 0.05f),
                            radius = trackRadius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Center spindle hole cutout (Dark cutout)
                    drawCircle(
                        color = VaultBlack,
                        radius = innerRadius,
                        center = center
                    )
                    
                    // Spindle inner ring details
                    drawCircle(
                        color = VaultSurfaceVariant,
                        radius = innerRadius * 1.1f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = innerRadius * 1.05f,
                        center = center,
                        style = Stroke(width = 0.5.dp.toPx())
                    )
                }
        )
    }
}
