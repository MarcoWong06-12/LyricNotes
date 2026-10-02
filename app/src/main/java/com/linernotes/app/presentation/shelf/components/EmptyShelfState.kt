package com.linernotes.app.presentation.shelf.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyShelfState(modifier: Modifier = Modifier) {
    val strings = com.linernotes.app.core.i18n.LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.width / 2

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF282830), Color(0xFF121215)),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            for (r in listOf(0.85f, 0.72f, 0.60f, 0.48f)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = outerRadius * r,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            drawCircle(
                color = Color(0xFF8A3B2C),
                radius = outerRadius * 0.32f,
                center = center
            )

            drawCircle(
                color = Color(0xFF131316),
                radius = outerRadius * 0.08f,
                center = center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.emptyShelfTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = strings.emptyShelfSubtitle,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
