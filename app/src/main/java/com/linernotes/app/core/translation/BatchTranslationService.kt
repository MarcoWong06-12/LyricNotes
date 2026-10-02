package com.linernotes.app.core.translation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.linernotes.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 专辑批量翻译前台服务
 * 搭配 ForegroundService 与 WakeLock，保证在锁屏、切后台或屏幕关闭时 AI 翻译平稳推进不被系统杀死。
 */
@AndroidEntryPoint
class BatchTranslationService : Service() {

    @Inject
    lateinit var batchManager: BatchTranslationManager

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "album_translation_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.linernotes.app.ACTION_START_BATCH_TRANSLATION"
        const val ACTION_STOP = "com.linernotes.app.ACTION_STOP_BATCH_TRANSLATION"
        const val EXTRA_ALBUM_ID = "extra_album_id"
        const val EXTRA_ALBUM_TITLE = "extra_album_title"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 立即展示前台通知，满足 Android 14 启动 5 秒内必须调用 startForeground 的严格要求
        val initialNotification = buildNotification("正在准备翻译专辑...", "", 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        acquireWakeLock()

        // 持续观察 BatchTranslationManager 的进度变化并动态刷新通知栏进度
        serviceScope.launch {
            batchManager.state.collectLatest { state ->
                if (state.isTranslating) {
                    val title = "正在翻译《${state.albumTitle ?: "专辑"}》"
                    val content = if (state.totalTracks > 0) {
                        "[${state.currentTrackIndex}/${state.totalTracks}] ${state.currentTrackTitle ?: ""}"
                    } else {
                        state.currentTrackTitle ?: "准备中..."
                    }
                    val notification = buildNotification(title, content, state.totalTracks, state.currentTrackIndex)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } else {
                    stopForegroundAndService()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                batchManager.cancelBatchTranslation()
                stopForegroundAndService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // 已在 onCreate 激活前台通知与观察
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LinerNotes:BatchTranslationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // 30 分钟超时保底
            }
        } catch (e: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
        } finally {
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "专辑歌词后台翻译",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "展示专辑整张批量翻译的实时进度"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        maxProgress: Int,
        currentProgress: Int
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BatchTranslationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "取消", stopPendingIntent)

        if (maxProgress > 0) {
            builder.setProgress(maxProgress, currentProgress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun stopForegroundAndService() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
