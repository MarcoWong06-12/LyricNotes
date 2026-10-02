package com.linernotes.app.core.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * 基于 Android NotificationListenerService 与 MediaSessionManager 的核心媒体同步服务
 * 实时捕获 Spotify 及主流流媒体应用的曲目信息、专辑封面、播放状态与毫秒级进度
 */
class MediaPlaybackSyncService : NotificationListenerService(), MediaControlActionHandler {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private val playbackStateManager = PlaybackStateManager.getInstance()

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
            activeController = null
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
            activeController = chosen
            chosen.registerCallback(controllerCallback)

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
        val coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        val pkg = activeController?.packageName ?: ""
        val sourceApp = MediaSourceApp.fromPackageName(pkg)

        if (title.isNotBlank()) {
            val current = playbackStateManager.playbackState.value
            playbackStateManager.updateState(
                current.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = if (duration > 0) duration else current.durationMs,
                    coverUrl = coverUri ?: current.coverUrl,
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
