package com.linernotes.app.presentation.nowplaying.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.presentation.common.bouncyClickable

/**
 * 空闲等待页 (干净纯粹的 Spotify 引导)
 */
@Composable
fun NowPlayingIdleContent(
    isDark: Boolean,
    isNotificationGranted: Boolean,
    onGrantPermission: () -> Unit,
    onLaunchSpotify: () -> Unit
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.35f)),
            shadowElevation = 12.dp,
            modifier = Modifier.size(92.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.appName,
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.waitingForPlaybackSubtitle,
            color = if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isNotificationGranted) {
            Surface(
                color = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF1DB954).copy(alpha = 0.30f) else Color(0xFF1DB954).copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.openNotificationPermission,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = strings.openNotificationPermissionDesc,
                            color = if (isDark) Color.White.copy(alpha = 0.50f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Surface(
                        color = Color(0xFF1DB954),
                        shape = CircleShape,
                        modifier = Modifier
                            .bouncyClickable(
                                pressedScale = 0.92f,
                                onClick = onGrantPermission
                            )
                    ) {
                        Text(
                            text = strings.grantPermissionAction,
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        Surface(
            color = Color(0xFF1DB954),
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .bouncyClickable(
                    pressedScale = 0.96f,
                    onClick = onLaunchSpotify
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.openMusicAppBtn,
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
