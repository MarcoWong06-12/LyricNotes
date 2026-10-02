package com.linernotes.app.core.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.FileOutputStream

/**
 * 基于 Android NotificationListenerService 与 MediaSessionManager 的核心媒体同步服务
 * 实时捕获 Spotify 及主流流媒体应用的曲目信息、专辑封面、播放状态与毫秒级进度
 */
class MediaPlaybackSyncService : NotificationListenerService(), MediaControlActionHandler {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var activeCompatController: MediaControllerCompat? = null
    private val playbackStateManager = PlaybackStateManager.getInstance()

    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) {
            val isShuffle = shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
            val current = playbackStateManager.playbackState.value
            playbackStateManager.updateState(current.copy(isShuffleActive = isShuffle))
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                else -> 0
            }
            val current = playbackStateManager.playbackState.value
            playbackStateManager.updateState(current.copy(repeatMode = mapped))
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            val items = queue?.map { item ->
                QueueTrackItem(
                    id = item.queueId,
                    title = item.description.title?.toString() ?: "",
                    artist = item.description.subtitle?.toString() ?: "",
                    album = item.description.description?.toString() ?: "",
                    coverUri = item.description.iconUri?.toString()
                )
            } ?: emptyList()
            val current = playbackStateManager.playbackState.value
            playbackStateManager.updateState(current.copy(queueItems = items))
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state ?: return
            handlePlaybackStateUpdate(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata ?: return
            handleMetadataUpdate(metadata)
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            findAndAttachActiveController()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            inspectAndAttachController(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        playbackStateManager.registerControlHandler(this)
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, MediaPlaybackSyncService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            inspectAndAttachController(controllers)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        playbackStateManager.unregisterControlHandler(this)
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            activeController?.unregisterCallback(controllerCallback)
            activeCompatController?.unregisterCallback(compatCallback)
            activeController = null
            activeCompatController = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val pkg = sbn?.packageName ?: return
        // 当收到来自 Spotify 的通知时，尝试确保已绑定
        if (pkg == "com.spotify.music" && (activeController == null || activeController?.packageName != pkg)) {
            findAndAttachActiveController()
        }

        // 尝试从 Spotify 通知栏中抓取大图作为兜底封面
        if (pkg == "com.spotify.music") {
            try {
                val extras = sbn.notification?.extras
                @Suppress("DEPRECATION")
                val notifBitmap = (extras?.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                    ?: (extras?.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                if (notifBitmap != null) {
                    val localCoverPath = saveBitmapToLocalFile(notifBitmap)
                    if (localCoverPath != null) {
                        val current = playbackStateManager.playbackState.value
                        if (current.coverUrl.isNullOrBlank()) {
                            playbackStateManager.updateState(current.copy(coverUrl = localCoverPath))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var coverSequence = 0

    private fun saveBitmapToLocalFile(bitmap: Bitmap): String? {
        return try {
            coverSequence++
            val artFile = File(cacheDir, "media_art_${System.currentTimeMillis()}_$coverSequence.png")
            FileOutputStream(artFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            cleanupOldArtFiles(artFile.name)
            artFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cleanupOldArtFiles(currentFileName: String) {
        try {
            cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("media_art_") && f.name != currentFileName) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun findAndAttachActiveController() {
        try {
            val componentName = ComponentName(this, MediaPlaybackSyncService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            inspectAndAttachController(controllers)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun inspectAndAttachController(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        // 优先匹配 Spotify，其次挑选正在处于 PLAYING 状态的媒体控制器
        val chosen = controllers.firstOrNull { it.packageName == "com.spotify.music" }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        if (chosen != null && chosen != activeController) {
            activeController?.unregisterCallback(controllerCallback)
            activeCompatController?.unregisterCallback(compatCallback)
            activeController = chosen
            chosen.registerCallback(controllerCallback)

            try {
                val tokenCompat = MediaSessionCompat.Token.fromToken(chosen.sessionToken)
                val compat = MediaControllerCompat(this, tokenCompat)
                activeCompatController = compat
                compat.registerCallback(compatCallback)

                val initShuffle = compat.shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
                val initRepeat = when (compat.repeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                    PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                    else -> 0
                }
                val initQueue = compat.queue?.map { item ->
                    QueueTrackItem(
                        id = item.queueId,
                        title = item.description.title?.toString() ?: "",
                        artist = item.description.subtitle?.toString() ?: "",
                        album = item.description.description?.toString() ?: "",
                        coverUri = item.description.iconUri?.toString()
                    )
                } ?: emptyList()

                val cur = playbackStateManager.playbackState.value
                playbackStateManager.updateState(
                    cur.copy(
                        isShuffleActive = initShuffle,
                        repeatMode = initRepeat,
                        queueItems = initQueue
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            chosen.metadata?.let { handleMetadataUpdate(it) }
            chosen.playbackState?.let { handlePlaybackStateUpdate(it) }
        }
    }

    private fun handleMetadataUpdate(metadata: MediaMetadata) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        // 1. 优先提取系统跨进程解包的专辑封面 Bitmap (Spotify 原生以 Bitmap 传递)
        val rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.description?.iconBitmap
            ?: activeCompatController?.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: activeCompatController?.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: activeCompatController?.metadata?.description?.iconBitmap

        var coverUri = rawBitmap?.let { saveBitmapToLocalFile(it) }

        // 2. 若没有 Bitmap，过滤掉无法跨进程读取的 content:// 协议，仅保留可直接下载的 HTTP/HTTPS 或本地文件
        if (coverUri == null) {
            val candidateUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ?: metadata.description?.iconUri?.toString()
            if (!candidateUri.isNullOrBlank() && (candidateUri.startsWith("http://") || candidateUri.startsWith("https://") || candidateUri.startsWith("file://"))) {
                coverUri = candidateUri
            }
        }

        val pkg = activeController?.packageName ?: ""
        val sourceApp = MediaSourceApp.fromPackageName(pkg)

        if (title.isNotBlank()) {
            val current = playbackStateManager.playbackState.value
            val isSameTrack = (title == current.title && artist == current.artist)
            // 切歌时绝不沿用上一首的旧封面！若新封面暂未获取，重置为 null 待 Genius 或通知栏回填
            val resolvedCover = if (coverUri != null) coverUri else if (isSameTrack) current.coverUrl else null

            playbackStateManager.updateState(
                current.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = if (duration > 0) duration else current.durationMs,
                    coverUrl = resolvedCover,
                    packageName = pkg,
                    sourceApp = sourceApp,
                    lastUpdateTimeMs = SystemClock.elapsedRealtime()
                )
            )
        }
    }

    private fun handlePlaybackStateUpdate(state: PlaybackState) {
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        val position = state.position
        val speed = if (state.playbackSpeed > 0) state.playbackSpeed else 1.0f

        val current = playbackStateManager.playbackState.value
        playbackStateManager.updateState(
            current.copy(
                isPlaying = isPlaying,
                currentPositionMs = position,
                playbackSpeed = speed,
                lastUpdateTimeMs = SystemClock.elapsedRealtime()
            )
        )
    }

    // MediaControlActionHandler 实现 (直接远程控制播放器)
    override fun play() {
        activeController?.transportControls?.play()
    }

    override fun pause() {
        activeController?.transportControls?.pause()
    }

    override fun togglePlayPause() {
        val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    override fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        activeController?.transportControls?.seekTo(positionMs)
    }

    override fun toggleShuffle() {
        val compat = activeCompatController ?: return
        val currentShuffle = compat.shuffleMode
        val targetMode = if (currentShuffle == PlaybackStateCompat.SHUFFLE_MODE_ALL) {
            PlaybackStateCompat.SHUFFLE_MODE_NONE
        } else {
            PlaybackStateCompat.SHUFFLE_MODE_ALL
        }
        compat.transportControls.setShuffleMode(targetMode)
        val cur = playbackStateManager.playbackState.value
        playbackStateManager.updateState(cur.copy(isShuffleActive = targetMode != PlaybackStateCompat.SHUFFLE_MODE_NONE))
    }

    override fun cycleRepeatMode() {
        val compat = activeCompatController ?: return
        val currentRepeat = compat.repeatMode
        val (targetMode, mappedMode) = when (currentRepeat) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> Pair(PlaybackStateCompat.REPEAT_MODE_ALL, 1)
            PlaybackStateCompat.REPEAT_MODE_ALL -> Pair(PlaybackStateCompat.REPEAT_MODE_ONE, 2)
            PlaybackStateCompat.REPEAT_MODE_ONE -> Pair(PlaybackStateCompat.REPEAT_MODE_NONE, 0)
            else -> Pair(PlaybackStateCompat.REPEAT_MODE_ALL, 1)
        }
        compat.transportControls.setRepeatMode(targetMode)
        val cur = playbackStateManager.playbackState.value
        playbackStateManager.updateState(cur.copy(repeatMode = mappedMode))
    }

    companion object {
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
