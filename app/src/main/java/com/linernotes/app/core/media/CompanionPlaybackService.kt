package com.linernotes.app.core.media

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CompanionPlaybackService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.linernotes.app.ACTION_UPDATE_MEDIA"
        const val ACTION_STOP = "com.linernotes.app.ACTION_STOP_MEDIA"
        const val ACTION_PLAY_PAUSE = "com.linernotes.app.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.linernotes.app.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.linernotes.app.ACTION_PREVIOUS"

        private const val EXTRA_TRACK_TITLE = "extra_track_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_ALBUM_TITLE = "extra_album_title"
        private const val EXTRA_COVER_URL = "extra_cover_url"
        private const val EXTRA_IS_PLAYING = "extra_is_playing"
        private const val EXTRA_POS_MS = "extra_pos_ms"
        private const val EXTRA_DURATION_MS = "extra_duration_ms"
        private const val EXTRA_LYRIC = "extra_lyric"
        private const val EXTRA_ANNOTATION = "extra_annotation"
        private const val EXTRA_HAS_PREVIOUS = "extra_has_previous"
        private const val EXTRA_HAS_NEXT = "extra_has_next"

        fun update(context: Context, data: MediaPlaybackNotificationData) {
            val intent = Intent(context, CompanionPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TRACK_TITLE, data.trackTitle)
                putExtra(EXTRA_ARTIST, data.artist)
                putExtra(EXTRA_ALBUM_TITLE, data.albumTitle)
                putExtra(EXTRA_COVER_URL, data.coverUrl)
                putExtra(EXTRA_IS_PLAYING, data.isPlaying)
                putExtra(EXTRA_POS_MS, data.positionMs)
                putExtra(EXTRA_DURATION_MS, data.durationMs)
                putExtra(EXTRA_LYRIC, data.activeLyricSnippet)
                putExtra(EXTRA_ANNOTATION, data.activeAnnotationSnippet)
                putExtra(EXTRA_HAS_PREVIOUS, data.hasPrevious)
                putExtra(EXTRA_HAS_NEXT, data.hasNext)
            }
            try {
                if (data.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // 防极端后台启动限制
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CompanionPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @Inject
    lateinit var mediaNotificationManager: MediaNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_PLAY_PAUSE -> {
                mediaNotificationManager.dispatchPlayPause()
            }
            ACTION_NEXT -> {
                mediaNotificationManager.dispatchNext()
            }
            ACTION_PREVIOUS -> {
                mediaNotificationManager.dispatchPrevious()
            }
            ACTION_STOP -> {
                mediaNotificationManager.clear()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            ACTION_UPDATE -> {
                val data = MediaPlaybackNotificationData(
                    trackTitle = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: "",
                    artist = intent.getStringExtra(EXTRA_ARTIST) ?: "",
                    albumTitle = intent.getStringExtra(EXTRA_ALBUM_TITLE) ?: "",
                    coverUrl = intent.getStringExtra(EXTRA_COVER_URL),
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false),
                    positionMs = intent.getLongExtra(EXTRA_POS_MS, 0L),
                    durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L),
                    activeLyricSnippet = intent.getStringExtra(EXTRA_LYRIC),
                    activeAnnotationSnippet = intent.getStringExtra(EXTRA_ANNOTATION),
                    hasPrevious = intent.getBooleanExtra(EXTRA_HAS_PREVIOUS, false),
                    hasNext = intent.getBooleanExtra(EXTRA_HAS_NEXT, false)
                )

                serviceScope.launch {
                    val notification = mediaNotificationManager.buildNotification(data)
                    if (data.isPlaying) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(
                                MediaNotificationManager.NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            )
                        } else {
                            startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(false)
                        }
                        notificationManager.notify(MediaNotificationManager.NOTIFICATION_ID, notification)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
