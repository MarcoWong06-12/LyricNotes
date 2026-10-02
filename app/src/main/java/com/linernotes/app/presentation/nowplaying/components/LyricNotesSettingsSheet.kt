package com.linernotes.app.presentation.nowplaying.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linernotes.app.core.playback.TrackPlaybackState
import com.linernotes.app.presentation.nowplaying.FuriganaDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricNotesSettingsSheet(
    trackState: TrackPlaybackState,
    hasSongStory: Boolean,
    furiganaMode: FuriganaDisplayMode,
    isTraditionalChinese: Boolean,
    isDeCensorEnabled: Boolean,
    showPlaybackControls: Boolean,
    lyricOffsetMs: Long,
    onOpenSongStory: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSetFuriganaMode: (FuriganaDisplayMode) -> Unit,
    onToggleTraditionalChinese: () -> Unit,
    onToggleDeCensor: () -> Unit,
    onTogglePlaybackControls: () -> Unit,
    onReloadLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E24),
        contentColor = Color.White,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部曲目与播放状态卡片
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!trackState.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = trackState.coverUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (trackState.hasValidTrack) trackState.title else "未检测到播放曲目",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (trackState.hasValidTrack) trackState.artist else "请在 Spotify 播放音乐",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (trackState.isSpotify) Color(0xFF1DB954).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = trackState.sourceApp.displayName,
                            color = if (trackState.isSpotify) Color(0xFF1ED760) else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. Genius 歌曲创作背景 (Song Story)
            if (hasSongStory) {
                Surface(
                    color = Color(0xFFFFD54F).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenSongStory()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💡", fontSize = 20.sp)
                            Column {
                                Text(
                                    text = "Genius 歌曲创作背景与乐评",
                                    color = Color(0xFFFFE082),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = "查看作者创作灵感、访谈与深度文化隐喻",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFFFE082)
                        )
                    }
                }
            }

            // 3. 歌词时间轴微调 (Lyrics Offset)
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "歌词时间轴微调",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (lyricOffsetMs == 0L) "准时" else "${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs} ms",
                            color = if (lyricOffsetMs == 0L) Color.White.copy(alpha = 0.5f) else Color(0xFF1ED760),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OffsetButton("-0.5s", onClick = { onAdjustOffset(-500) }, modifier = Modifier.weight(1f))
                        OffsetButton("-0.2s", onClick = { onAdjustOffset(-200) }, modifier = Modifier.weight(1f))
                        OffsetButton("复位", onClick = onResetOffset, modifier = Modifier.weight(1f), isReset = true)
                        OffsetButton("+0.2s", onClick = { onAdjustOffset(200) }, modifier = Modifier.weight(1f))
                        OffsetButton("+0.5s", onClick = { onAdjustOffset(500) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // 4. 显示与排版设置
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // 底部播放控制器开关 (Lyricify 风格纯净沉浸模式)
                    SettingsSwitchRow(
                        title = "底部播放控制栏",
                        subtitle = "关闭以开启全屏纯净歌词模式（Lyricify 风格）",
                        checked = showPlaybackControls,
                        onCheckedChange = { onTogglePlaybackControls() }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // 歌词脏字脱敏与审查还原
                    SettingsSwitchRow(
                        title = "歌词脏字审查还原",
                        subtitle = "自动将国内源星号屏蔽词（如 f***）还原为艺术原貌",
                        checked = isDeCensorEnabled,
                        onCheckedChange = { onToggleDeCensor() }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // 繁简转换
                    SettingsSwitchRow(
                        title = "繁体中文显示",
                        subtitle = "将中文歌词与译文无损转为繁体中文",
                        checked = isTraditionalChinese,
                        onCheckedChange = { onToggleTraditionalChinese() }
                    )
                }
            }

            // 5. 日语假名注音 (Furigana) 模式选择
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "日语假名注音（Furigana）",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FuriganaPill(
                            title = "关闭",
                            isSelected = furiganaMode == FuriganaDisplayMode.OFF,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.OFF) },
                            modifier = Modifier.weight(1f)
                        )
                        FuriganaPill(
                            title = "平假名注音",
                            isSelected = furiganaMode == FuriganaDisplayMode.HIRAGANA,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.HIRAGANA) },
                            modifier = Modifier.weight(1f)
                        )
                        FuriganaPill(
                            title = "罗马音",
                            isSelected = furiganaMode == FuriganaDisplayMode.ROMAJI,
                            onClick = { onSetFuriganaMode(FuriganaDisplayMode.ROMAJI) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. 快捷操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onReloadLyrics()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重载歌词", fontSize = 13.sp, color = Color.White)
                }

                Button(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                            context.startActivity(webIntent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("打开 Spotify", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OffsetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isReset: Boolean = false
) {
    Surface(
        color = if (isReset) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isReset) Color.White else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = if (isReset) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1DB954),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun FuriganaPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) Color(0xFF1DB954) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
