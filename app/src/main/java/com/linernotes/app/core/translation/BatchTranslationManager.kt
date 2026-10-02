package com.linernotes.app.core.translation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.TranslationService
import com.linernotes.app.domain.repository.AlbumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专辑全量批量翻译调度管理器
 * 负责全局单例维护批量翻译生命周期、限制速率并发、持久化写入数据库并与前台通知服务协同。
 */
@Singleton
class BatchTranslationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlbumRepository,
    private val translationService: TranslationService,
    private val aiPreferences: AiPreferences
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null

    private val _state = MutableStateFlow(BatchTranslationState())
    val state: StateFlow<BatchTranslationState> = _state.asStateFlow()

    fun startBatchTranslation(albumId: String, albumTitle: String) {
        if (_state.value.isTranslating) {
            Log.d("BatchTranslation", "当前已有一个正在进行的翻译任务，请等待完成或取消")
            return
        }

        batchJob?.cancel()
        batchJob = managerScope.launch {
            try {
                Log.d("BatchTranslation", "启动整张专辑批量翻译: 《$albumTitle》")

                // 1. 获取专辑所有曲目并筛选有歌词的曲目
                val albumWithTracks = repository.getAlbumBookletStream(albumId).first()
                val candidateTracks = albumWithTracks?.tracks?.filter { !it.originalLyrics.isNullOrBlank() } ?: emptyList()

                if (candidateTracks.isEmpty()) {
                    Log.d("BatchTranslation", "专辑中未找到包含歌词的曲目")
                    _state.update {
                        it.copy(
                            isTranslating = false,
                            userMessage = "该专辑中暂无包含歌词的曲目"
                        )
                    }
                    return@launch
                }

                val total = candidateTracks.size
                _state.update {
                    it.copy(
                        isTranslating = true,
                        albumId = albumId,
                        albumTitle = albumTitle,
                        currentTrackTitle = "准备中...",
                        currentTrackIndex = 0,
                        totalTracks = total,
                        userMessage = "正在推敲翻译整张专辑..."
                    )
                }

                // 2. 启动前台保活服务
                startForegroundService(albumId, albumTitle)

                // 3. 第一轮顺畅推进（单轨顺延 + 缓冲延时，彻底杜绝瞬间并发击穿中转站频率阈值）
                val successfulTracks = mutableListOf<TrackEntity>()
                val failedTracks = mutableListOf<TrackEntity>()

                for ((index, track) in candidateTracks.withIndex()) {
                    if (!isActive) break
                    _state.update {
                        it.copy(
                            currentTrackTitle = track.title,
                            currentTrackIndex = index + 1
                        )
                    }

                    try {
                        val result = translationService.translateTrack(
                            trackTitle = track.title,
                            originalLyrics = track.originalLyrics!!
                        )
                        repository.updateTrackTranslation(
                            trackId = track.id,
                            translatedTitle = result.translatedTitle ?: track.translatedTitle,
                            originalLyrics = track.originalLyrics,
                            translatedLyrics = result.translatedLyrics
                        )
                        successfulTracks.add(track)
                        Log.d("BatchTranslation", "《${track.title}》翻译完成并已落库")
                    } catch (e: Exception) {
                        failedTracks.add(track)
                        Log.e("BatchTranslation", "《${track.title}》第一轮暂未完成: ${e.message}，已加入末尾补译队列")
                    }

                    // 歌曲之间保持 400ms 微小缓冲，避免 API 判定为恶意突发扫描
                    delay(400)
                }

                // 4. 第二轮失败补偿修复机制 (Second-Pass Recovery)
                if (failedTracks.isNotEmpty() && isActive) {
                    Log.d("BatchTranslation", "首轮存在 ${failedTracks.size} 首未完成，2 秒后启动二次补偿修复...")
                    _state.update { it.copy(userMessage = "首轮有 ${failedTracks.size} 首歌曲受阻，正在自动执行二次补偿...") }
                    delay(2000)

                    val stillFailed = mutableListOf<TrackEntity>()
                    for (failedTrack in failedTracks) {
                        if (!isActive) break
                        _state.update { it.copy(currentTrackTitle = "【补译】${failedTrack.title}") }
                        try {
                            val result = translationService.translateTrack(
                                trackTitle = failedTrack.title,
                                originalLyrics = failedTrack.originalLyrics!!
                            )
                            repository.updateTrackTranslation(
                                trackId = failedTrack.id,
                                translatedTitle = result.translatedTitle ?: failedTrack.translatedTitle,
                                originalLyrics = failedTrack.originalLyrics,
                                translatedLyrics = result.translatedLyrics
                            )
                            successfulTracks.add(failedTrack)
                            Log.d("BatchTranslation", "《${failedTrack.title}》补偿翻译成功并已写入本地")
                        } catch (e: Exception) {
                            stillFailed.add(failedTrack)
                            Log.e("BatchTranslation", "《${failedTrack.title}》二次重试仍未成功: ${e.message}")
                        }
                        delay(600)
                    }
                    failedTracks.clear()
                    failedTracks.addAll(stillFailed)
                }

                val finalSuccess = successfulTracks.size
                val finalFailed = failedTracks.size
                val finishMsg = if (finalFailed == 0) {
                    "整张专辑翻译完成！（共 $total 首全部成功）"
                } else {
                    "整张专辑翻译结束：已完成 $finalSuccess 首，${finalFailed} 首未完成（可在单曲页重试）"
                }

                Log.d("BatchTranslation", finishMsg)
                _state.update {
                    it.copy(
                        isTranslating = false,
                        userMessage = finishMsg
                    )
                }
            } catch (e: Exception) {
                Log.e("BatchTranslation", "批量翻译异常: ${e.message}")
                _state.update {
                    it.copy(
                        isTranslating = false,
                        userMessage = "批量翻译遇到错误: ${e.message}"
                    )
                }
            } finally {
                stopForegroundService()
            }
        }
    }

    /**
     * 后台静默为专辑中缺失翻译的曲目自动补全翻译（保留已有优质翻译，绝不重写覆盖）
     */
    fun autoFillMissingTranslations(albumId: String, albumTitle: String, inputTracks: List<TrackEntity>? = null) {
        managerScope.launch {
            try {
                val candidateTracks = inputTracks?.takeIf { it.isNotEmpty() }
                    ?: repository.getAlbumBookletStream(albumId).first()?.tracks ?: emptyList()
                val missingTracks = candidateTracks.filter { 
                    !it.originalLyrics.isNullOrBlank() && it.translatedLyrics.isNullOrBlank() 
                }

                if (missingTracks.isEmpty()) {
                    Log.d("BatchTranslation", "《$albumTitle》所有歌曲均已具备翻译，无需自动补齐")
                    return@launch
                }

                Log.d("BatchTranslation", "《$albumTitle》开始后台自动补齐缺失翻译，共 ${missingTracks.size} 首")
                var filledCount = 0
                for ((index, track) in missingTracks.withIndex()) {
                    if (!isActive) break
                    try {
                        val result = translationService.translateTrack(
                            trackTitle = track.title,
                            originalLyrics = track.originalLyrics!!
                        )
                        repository.updateTrackTranslation(
                            trackId = track.id,
                            translatedTitle = result.translatedTitle ?: track.translatedTitle,
                            originalLyrics = track.originalLyrics,
                            translatedLyrics = result.translatedLyrics
                        )
                        filledCount++
                        Log.d("BatchTranslation", "已自动补齐《${track.title}》翻译 [${index + 1}/${missingTracks.size}]")
                    } catch (e: Exception) {
                        Log.e("BatchTranslation", "自动补译《${track.title}》失败: ${e.message}")
                    }
                    delay(300)
                }
            } catch (e: Exception) {
                Log.e("BatchTranslation", "autoFillMissingTranslations 异常: ${e.message}")
            }
        }
    }

    fun cancelBatchTranslation() {
        if (_state.value.isTranslating) {
            batchJob?.cancel()
            batchJob = null
            _state.update {
                it.copy(
                    isTranslating = false,
                    userMessage = "已取消批量翻译"
                )
            }
            stopForegroundService()
            Log.d("BatchTranslation", "用户取消了专辑批量翻译")
        }
    }

    private fun startForegroundService(albumId: String, albumTitle: String) {
        try {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = BatchTranslationService.ACTION_START
                putExtra(BatchTranslationService.EXTRA_ALBUM_ID, albumId)
                putExtra(BatchTranslationService.EXTRA_ALBUM_TITLE, albumTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("BatchTranslation", "前台服务启动异常: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = BatchTranslationService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("BatchTranslation", "前台服务关闭异常: ${e.message}")
        }
    }
}
