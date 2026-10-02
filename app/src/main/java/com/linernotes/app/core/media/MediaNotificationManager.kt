package com.linernotes.app.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.linernotes.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "liner_notes_playback_channel"
        private const val CHANNEL_NAME = "唱片伴侣与歌词同步"

        const val ACTION_PLAY_PAUSE = "com.linernotes.app.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.linernotes.app.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.linernotes.app.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.linernotes.app.ACTION_STOP"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var cachedCoverUrl: String? = null
    private var cachedCoverBitmap: Bitmap? = null

    var commandHandler: PlaybackCommandHandler? = null

    val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "LinerNotesMediaSession").apply {
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                commandHandler?.onPlay()
            }

            override fun onPause() {
                commandHandler?.onPause()
            }

            override fun onSkipToNext() {
                commandHandler?.onNext()
            }

            override fun onSkipToPrevious() {
                commandHandler?.onPrevious()
            }

            override fun onSeekTo(pos: Long) {
                commandHandler?.onSeekTo(pos)
            }

            override fun onStop() {
                commandHandler?.onPause()
                clear()
            }
        })
        isActive = true
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示当前 CD/数字唱片伴侣播放进度、中英歌词与 Genius 典故提要"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun dispatchPlayPause() {
        commandHandler?.onTogglePlay()
    }

    fun dispatchNext() {
        commandHandler?.onNext()
    }

    fun dispatchPrevious() {
        commandHandler?.onPrevious()
    }

    suspend fun buildNotification(data: MediaPlaybackNotificationData): Notification {
        // 1. 异步获取软件级 Bitmap 封面（allowHardware=false 防跨进程 IPC 崩溃）
        val coverBitmap = loadCoverBitmap(data.coverUrl)

        // 2. 更新 MediaSession 状态与元数据（使 Android 11+ 原生波形进度条与画板就绪）
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (data.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                data.positionMs,
                if (data.isPlaying) 1.0f else 0.0f
            )
        mediaSession.setPlaybackState(playbackStateBuilder.build())

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, data.trackTitle.ifBlank { "正在播放" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, data.artist.ifBlank { "LinerNotes" })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, data.albumTitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, data.durationMs.coerceAtLeast(0L))

        if (coverBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, coverBitmap)
        }
        mediaSession.setMetadata(metadataBuilder.build())
        if (!mediaSession.isActive) {
            mediaSession.isActive = true
        }

        // 3. 构建 PendingIntents
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(context, CompanionPlaybackService::class.java).setAction(ACTION_PREVIOUS)
        val prevPendingIntent = PendingIntent.getService(
            context,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(context, CompanionPlaybackService::class.java).setAction(ACTION_PLAY_PAUSE)
        val playPausePendingIntent = PendingIntent.getService(
            context,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, CompanionPlaybackService::class.java).setAction(ACTION_NEXT)
        val nextPendingIntent = PendingIntent.getService(
            context,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, CompanionPlaybackService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            context,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. 构建副标题文字：优先精选 Genius 典故，次选当前歌词
        val subTextInfo = when {
            !data.activeAnnotationSnippet.isNullOrBlank() -> "💡 典故: ${data.activeAnnotationSnippet}"
            !data.activeLyricSnippet.isNullOrBlank() -> data.activeLyricSnippet
            data.albumTitle.isNotBlank() -> data.albumTitle
            else -> null
        }

        val contentSecondary = listOfNotNull(
            data.artist.takeIf { it.isNotBlank() },
            data.albumTitle.takeIf { it.isNotBlank() }
        ).joinToString(" — ")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(data.trackTitle.ifBlank { "LinerNotes" })
            .setContentText(contentSecondary)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(data.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (subTextInfo != null) {
            builder.setSubText(subTextInfo)
        }

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        // 添加上一曲、播放/暂停、下一曲操作按钮
        builder.addAction(
            android.R.drawable.ic_media_previous,
            "上一曲",
            prevPendingIntent
        )

        builder.addAction(
            if (data.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (data.isPlaying) "暂停" else "播放",
            playPausePendingIntent
        )

        builder.addAction(
            android.R.drawable.ic_media_next,
            "下一曲",
            nextPendingIntent
        )

        // 应用 MediaStyle 样式并绑定 MediaSession Token
        builder.setStyle(
            MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )

        return builder.build()
    }

    private suspend fun loadCoverBitmap(coverUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (coverUrl.isNullOrBlank()) return@withContext null
        if (coverUrl == cachedCoverUrl && cachedCoverBitmap != null && !cachedCoverBitmap!!.isRecycled) {
            return@withContext cachedCoverBitmap
        }

        try {
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false) // 杜绝 RenderThread 硬件位图跨进程崩溃
                .size(400, 400)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    cachedCoverUrl = coverUrl
                    cachedCoverBitmap = bitmap
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        null
    }

    fun clear() {
        mediaSession.isActive = false
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
