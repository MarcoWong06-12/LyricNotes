package com.linernotes.app.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.preference.AiPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    aiPreferences: AiPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val strings = LocalStrings.current

    var themeMode by remember { mutableStateOf(aiPreferences.themeMode) }
    var appLanguage by remember { mutableStateOf(aiPreferences.appLanguage) }
    var targetLanguage by remember { mutableStateOf(aiPreferences.targetLanguage) }
    var lyricsSource by remember { mutableStateOf(aiPreferences.lyricsSource) }
    var discogsToken by remember { mutableStateOf(aiPreferences.discogsToken) }
    var geniusToken by remember { mutableStateOf(aiPreferences.geniusToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.settingsDialogTitle, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ==========================================
                // 1. 语言与外观偏好专区
                // ==========================================
                Text(
                    text = strings.sectionLocalization,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 外观与主题模式 (跟随系统 / 浅色模式 / 深色模式)
                Text(strings.themeModeLabel, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val themeOptions = listOf(
                        AiPreferences.ThemeMode.SYSTEM.code to strings.themeModeSystem,
                        AiPreferences.ThemeMode.LIGHT.code to strings.themeModeLight,
                        AiPreferences.ThemeMode.DARK.code to strings.themeModeDark
                    )
                    themeOptions.forEach { (code, label) ->
                        FilterChip(
                            selected = themeMode == code,
                            onClick = {
                                themeMode = code
                                aiPreferences.themeMode = code
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 应用界面语言
                Text(strings.appLanguageLabel, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = appLanguage == lang.code,
                            onClick = {
                                appLanguage = lang.code
                                aiPreferences.appLanguage = lang.code
                            },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 歌词翻译目标语言
                Text(strings.targetLanguageLabel, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TranslationTargetLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = targetLanguage == lang.code,
                            onClick = {
                                targetLanguage = lang.code
                                aiPreferences.targetLanguage = lang.code
                            },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==========================================
                // 2. 官方歌词源检索偏好专区
                // ==========================================
                Text(
                    text = strings.lyricsSourceSetting,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "系统将优先检索各大音乐平台的官方双语或原版歌词；若检索到的歌词暂无中文翻译，系统将通过谷歌翻译引擎自动即时补全，免配置且完全免费。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                val sources = listOf(
                    AiPreferences.LyricsSourcePreference.AUTO_FIRST.code to strings.sourceAuto,
                    AiPreferences.LyricsSourcePreference.NETEASE_ONLY.code to strings.sourceOfficial,
                    AiPreferences.LyricsSourcePreference.QQ_ONLY.code to strings.sourceQq,
                    AiPreferences.LyricsSourcePreference.KUGOU_ONLY.code to strings.sourceKugou,
                    AiPreferences.LyricsSourcePreference.MUSIXMATCH_ONLY.code to strings.sourceMusixmatch,
                    AiPreferences.LyricsSourcePreference.LRCLIB_ONLY.code to strings.sourceLrclib
                )

                sources.forEach { (code, name) ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (lyricsSource == code) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .bouncyClickable {
                                lyricsSource = code
                                aiPreferences.lyricsSource = code
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = lyricsSource == code,
                                onClick = {
                                    lyricsSource = code
                                    aiPreferences.lyricsSource = code
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (lyricsSource == code) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==========================================
                // 3. Discogs 实体唱片库配置 (可选)
                // ==========================================
                Text(
                    text = "Discogs 唱片资料库 (可选)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "默认免 Token 即可检索。如遇请求频率限制（25次/分），可在 discogs.com/settings/developers 生成 Personal Access Token 填入以提升限额至 60次/分。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = discogsToken,
                    onValueChange = { discogsToken = it },
                    placeholder = { Text("Discogs Personal Access Token...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                // ==========================================
                // 4. Genius 歌词典故与故事库 (可选)
                // ==========================================
                Text(
                    text = "Genius 歌词典故与故事库 (可选)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "默认内置免密检索。如需更高并发或专属开发者配额，可在 genius.com/api-clients 申请 Client Access Token 填入。",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = geniusToken,
                    onValueChange = { geniusToken = it },
                    placeholder = { Text("Genius Client Access Token...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            BouncyButton(
                onClick = {
                    aiPreferences.themeMode = themeMode
                    aiPreferences.appLanguage = appLanguage
                    aiPreferences.targetLanguage = targetLanguage
                    aiPreferences.lyricsSource = lyricsSource
                    aiPreferences.discogsToken = discogsToken.trim()
                    aiPreferences.geniusToken = geniusToken.trim()
                    onSaved()
                }
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
