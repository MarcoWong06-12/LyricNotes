package com.linernotes.app.presentation.booklet.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.linernotes.app.presentation.common.BouncyButton
import com.linernotes.app.presentation.common.BouncyTonalButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.bluetooth.CdConnectionState
import com.linernotes.app.core.i18n.LocalStrings

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdSyncSheet(
    connectionState: CdConnectionState,
    connectedDeviceName: String?,
    pairedDevices: List<BluetoothDevice>,
    onConnect: (BluetoothDevice?) -> Unit,
    onDisconnect: () -> Unit,
    onOpenTracklist: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // 标题栏：Gemini 风格胶囊徽标
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
                        color = when (connectionState) {
                            CdConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                            CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (connectionState) {
                                    CdConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                    CdConnectionState.CONNECTING -> Icons.Default.Bluetooth
                                    else -> Icons.Default.Bluetooth
                                },
                                contentDescription = null,
                                tint = when (connectionState) {
                                    CdConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                    CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = strings.cdSyncTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shanling RFCOMM SyncLink",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // 状态胶囊药丸 (Gemini Pill)
                Surface(
                    shape = CircleShape,
                    color = when (connectionState) {
                        CdConnectionState.CONNECTED -> Color(0xFF1B5E20).copy(alpha = 0.25f)
                        CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        CdConnectionState.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        CdConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when (connectionState) {
                            CdConnectionState.CONNECTED -> Color(0xFF4CAF50).copy(alpha = 0.6f)
                            CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                            CdConnectionState.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            CdConnectionState.DISCONNECTED -> Color.White.copy(alpha = 0.12f)
                        }
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        CdConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                        CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                                        CdConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                        CdConnectionState.DISCONNECTED -> Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = when (connectionState) {
                                CdConnectionState.CONNECTED -> strings.cdSyncConnected
                                CdConnectionState.CONNECTING -> strings.cdSyncConnecting
                                CdConnectionState.FAILED -> "连接失败"
                                CdConnectionState.DISCONNECTED -> strings.cdSyncDisconnected
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when (connectionState) {
                                CdConnectionState.CONNECTED -> Color(0xFF81C784)
                                CdConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                                CdConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                CdConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 主控卡片 (Pill Container)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (connectionState == CdConnectionState.CONNECTED) {
                        Text(
                            text = "已连接设备: ${connectedDeviceName ?: "山灵 EC Mini"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "已建立双向控制通信，正在实时读取 CD 播放进度与音轨，歌词将自动同步精准滚动。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        BouncyButton(
                            onClick = {
                                onDismiss()
                                onOpenTracklist()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.cdTracklistAction)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        BouncyTonalButton(
                            onClick = onDisconnect,
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.cdSyncDisconnectAction)
                        }
                    } else {
                        Text(
                            text = "连接山灵 CD 机（如 EC Mini / EC3 / EC Smart）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "通过 SyncLink RFCOMM 蓝牙通道同步播放状态，手机将化身数字双语歌词内页，实时跟随 CD 时间轴滚动。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        BouncyButton(
                            onClick = { onConnect(null) },
                            enabled = connectionState != CdConnectionState.CONNECTING,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (connectionState == CdConnectionState.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.cdSyncConnecting)
                            } else {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.cdSyncConnectAction)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 已配对设备列表
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "系统已配对蓝牙设备",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("前往配对", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (pairedDevices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "未发现已配对的蓝牙设备，请先在手机系统设置中配对山灵 CD 机",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pairedDevices.take(6).forEach { device ->
                        val devName = try { device.name ?: "Unknown" } catch (_: Exception) { "Unknown" }
                        val isShanling = devName.contains("EC", ignoreCase = true) ||
                                devName.contains("Shanling", ignoreCase = true) ||
                                devName.contains("山灵")
                        val isCurrent = connectionState == CdConnectionState.CONNECTED && connectedDeviceName == devName

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else if (isShanling) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else Color.White.copy(alpha = 0.05f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.BluetoothConnected else Icons.Default.Album,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else if (isShanling) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = devName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isShanling) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isShanling) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "CD机",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = try { device.address } catch (_: Exception) { "" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                if (isCurrent) {
                                    BouncyTonalButton(
                                        onClick = onDisconnect,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("断开", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    BouncyTonalButton(
                                        onClick = { onConnect(device) },
                                        enabled = connectionState != CdConnectionState.CONNECTING,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("连接", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
