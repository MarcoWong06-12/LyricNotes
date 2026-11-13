package com.linernotes.app.core.playback

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    private val playbackStateManager by lazy { PlaybackStateManager.get(this) }
    private var ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            val controllerSnapshot = activeController
            ioScope.launch {
                val items = queue?.map { mapQueueItem(it) } ?: emptyList()
                if (activeController === controllerSnapshot) {
                    val current = playbackStateManager.playbackState.value
                    playbackStateManager.updateState(current.copy(queueItems = items))
                }
            }
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
        ioScope.cancel()
        ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        playbackStateManager.setNotificationAccessGranted(true)
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
        playbackStateManager.refreshNotificationAccess(this)
        ioScope.cancel()
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
        val current = playbackStateManager.playbackState.value

        val isMusicApp = pkg == "com.spotify.music" || pkg == "com.apple.android.music" ||
            pkg == "com.netease.cloudmusic" || pkg == "com.tencent.qqmusic" ||
            pkg == current.packageName || pkg.contains("music") || pkg.contains("audio") || pkg.contains("player")

        if (isMusicApp && (activeController == null || activeController?.packageName != pkg)) {
            findAndAttachActiveController()
        }

        if (isMusicApp && current.hasValidTrack) {
            val expectedTitle = current.title
            val expectedArtist = current.artist
            val expectedEpoch = current.trackEpoch
            val hasValidCover = !current.coverUrl.isNullOrBlank() &&
                (!current.coverUrl.startsWith("/") || File(current.coverUrl).exists())
            if (hasValidCover) return

            ioScope.launch {
                try {
                    val extras = sbn.notification?.extras
                    @Suppress("DEPRECATION")
                    val notifBitmap = (extras?.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                        ?: (extras?.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                        ?: run {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    sbn.notification?.getLargeIcon()?.loadDrawable(this@MediaPlaybackSyncService)?.let { drawable ->
                                        val bmp = Bitmap.createBitmap(
                                            drawable.intrinsicWidth.coerceAtLeast(1),
                                            drawable.intrinsicHeight.coerceAtLeast(1),
                                            Bitmap.Config.ARGB_8888
                                        )
                                        val canvas = android.graphics.Canvas(bmp)
                                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                                        drawable.draw(canvas)
                                        bmp
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            } else null
                        }
                    if (notifBitmap != null) {
                        val trackKey = "${expectedArtist}_${expectedTitle}"
                        val localCoverPath = saveBitmapToLocalFile(
                            bitmap = notifBitmap,
                            prefix = MEDIA_ART_PREFIX,
                            maxRetained = 4,
                            trackKey = trackKey
                        )
                        if (localCoverPath != null) {
                            playbackStateManager.updateCoverIfBetter(
                                expectedTitle = expectedTitle,
                                expectedArtist = expectedArtist,
                                coverUrl = localCoverPath,
                                quality = CoverQuality.NOTIFICATION_LOCAL,
                                expectedEpoch = expectedEpoch
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private val coverSequence = java.util.concurrent.atomic.AtomicInteger(0)

    private fun saveBitmapToLocalFile(
        bitmap: Bitmap,
        prefix: String,
        maxRetained: Int,
        trackKey: String = ""
    ): String? {
        return try {
            val keyHash = if (trackKey.isNotBlank()) {
                trackKey.hashCode().toUInt().toString(16)
            } else {
                coverSequence.incrementAndGet().toString()
            }
            val artFile = File(cacheDir, "${prefix}${keyHash}.png")
            if (!artFile.exists() || artFile.length() == 0L) {
                FileOutputStream(artFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
            }
            cleanupOldArtFiles(prefix, artFile.name, maxRetained)
            artFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cleanupOldArtFiles(prefix: String, currentFileName: String, maxRetained: Int) {
        try {
            val files = cacheDir.listFiles { _, name -> name.startsWith(prefix) } ?: return
            if (files.size > maxRetained) {
                files.filter { it.name != currentFileName }
                    .sortedByDescending { it.lastModified() }
                    .drop((maxRetained - 1).coerceAtLeast(1))
                    .forEach { it.delete() }
            }
        } catch (_: Exception) {
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

        val chosen = controllers.firstOrNull {
            it.packageName == "com.spotify.music" && it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.packageName == "com.spotify.music" }
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
                val rawQueue = compat.queue

                val cur = playbackStateManager.playbackState.value
                playbackStateManager.updateState(
                    cur.copy(
                        isShuffleActive = initShuffle,
                        repeatMode = initRepeat
                    )
                )

                if (!rawQueue.isNullOrEmpty()) {
                    ioScope.launch {
                        val mappedQueue = rawQueue.map { mapQueueItem(it) }
                        if (activeController === chosen) {
                            val latest = playbackStateManager.playbackState.value
                            playbackStateManager.updateState(latest.copy(queueItems = mappedQueue))
                        }
                    }
                }
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

        val current = playbackStateManager.playbackState.value
        val isSameTrack = (title == current.title && artist == current.artist)
        val hasExistingValidCover = isSameTrack && !current.coverUrl.isNullOrBlank() &&
            (!current.coverUrl.startsWith("/") || File(current.coverUrl).exists())

        val candidateUri = if (hasExistingValidCover) null else {
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ?: metadata.description?.iconUri?.toString()
        }

        val immediateUri = when {
            hasExistingValidCover -> current.coverUrl
            !candidateUri.isNullOrBlank() && (
                candidateUri.startsWith("http://") ||
                    candidateUri.startsWith("https://") ||
                    candidateUri.startsWith("file://")
                ) -> candidateUri
            else -> null
        }

        val pkg = activeController?.packageName ?: ""
        val sourceApp = MediaSourceApp.fromPackageName(pkg)

        if (title.isNotBlank()) {
            val resolvedCover = if (!immediateUri.isNullOrBlank()) immediateUri else if (isSameTrack) current.coverUrl else null
            val resolvedPosition = if (isSameTrack) current.currentPositionMs else 0L
            val resolvedDuration = if (duration > 0) duration else if (isSameTrack) current.durationMs else 0L

            playbackStateManager.updateState(
                current.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = resolvedDuration,
                    currentPositionMs = resolvedPosition,
                    coverUrl = resolvedCover,
                    packageName = pkg,
                    sourceApp = sourceApp,
                    lastUpdateTimeMs = if (isSameTrack) current.lastUpdateTimeMs else SystemClock.elapsedRealtime()
                )
            )

            if (!hasExistingValidCover) {
                val trackKey = "${artist}_${title}"
                val rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata.description?.iconBitmap
                    ?: activeCompatController?.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    ?: activeCompatController?.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
                    ?: activeCompatController?.metadata?.description?.iconBitmap
                val bitmapCopy = rawBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                    try { bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { bmp }
                }

                if (bitmapCopy != null || (!candidateUri.isNullOrBlank() && candidateUri.startsWith("content://"))) {
                    val expectedEpoch = playbackStateManager.playbackState.value.trackEpoch
                    ioScope.launch {
                        var savedPath: String? = bitmapCopy?.let {
                            saveBitmapToLocalFile(it, MEDIA_ART_PREFIX, maxRetained = 4, trackKey = trackKey)
                        }
                        if (savedPath == null && !candidateUri.isNullOrBlank() && candidateUri.startsWith("content://")) {
                            savedPath = try {
                                val stream = contentResolver.openInputStream(Uri.parse(candidateUri))
                                val contentBitmap = stream?.use { BitmapFactory.decodeStream(it) }
                                if (contentBitmap != null) {
                                    saveBitmapToLocalFile(contentBitmap, MEDIA_ART_PREFIX, maxRetained = 4, trackKey = trackKey)
                                } else {
                                    candidateUri
                                }
                            } catch (_: Exception) {
                                candidateUri
                            }
                        }
                        if (!savedPath.isNullOrBlank()) {
                            playbackStateManager.updateCoverIfBetter(
                                expectedTitle = title,
                                expectedArtist = artist,
                                coverUrl = savedPath,
                                quality = CoverQuality.NATIVE_METADATA,
                                expectedEpoch = expectedEpoch
                            )
                        }
                    }
                }
            }
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

    override fun play() {
        activeController?.transportControls?.play()
    }

    override fun pause() {
        activeController?.transportControls?.pause()
    }

    override fun togglePlayPause() {
        val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) pause() else play()
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

    override fun skipToQueueItem(item: QueueTrackItem) {
        val compat = activeCompatController
        val native = activeController

        val actions = compat?.playbackState?.actions
            ?: native?.playbackState?.actions
            ?: 0L

        val supportsQueue = (actions and PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM) != 0L
        val supportsUri = (actions and PlaybackStateCompat.ACTION_PLAY_FROM_URI) != 0L
        val supportsMediaId = (actions and PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID) != 0L

        val hasValidQueueId = item.id != -1L
        var triggered = false

        if (supportsQueue && hasValidQueueId) {
            try {
                compat?.transportControls?.skipToQueueItem(item.id)
                    ?: native?.transportControls?.skipToQueueItem(item.id)
                triggered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!triggered && supportsUri && !item.mediaUri.isNullOrBlank()) {
            try {
                val parsed = Uri.parse(item.mediaUri)
                compat?.transportControls?.playFromUri(parsed, null)
                    ?: native?.transportControls?.playFromUri(parsed, null)
                triggered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!triggered && supportsMediaId && !item.mediaId.isNullOrBlank()) {
            try {
                compat?.transportControls?.playFromMediaId(item.mediaId, null)
                    ?: native?.transportControls?.playFromMediaId(item.mediaId, null)
                triggered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!triggered && hasValidQueueId) {
            try {
                compat?.transportControls?.skipToQueueItem(item.id)
                    ?: native?.transportControls?.skipToQueueItem(item.id)
                triggered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!triggered && !item.mediaId.isNullOrBlank()) {
            try {
                compat?.transportControls?.playFromMediaId(item.mediaId, null)
                    ?: native?.transportControls?.playFromMediaId(item.mediaId, null)
                triggered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!triggered && item.title.isNotBlank()) {
            try {
                val query = if (item.artist.isNotBlank()) "${item.title} ${item.artist}" else item.title
                compat?.transportControls?.playFromSearch(query, null)
                    ?: native?.transportControls?.playFromSearch(query, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun mapQueueItem(item: MediaSessionCompat.QueueItem): QueueTrackItem {
        val desc = item.description
        val rawTitle = desc.title?.toString()?.trim().orEmpty()
        val rawSubtitle = desc.subtitle?.toString()?.trim().orEmpty()
        val rawDesc = desc.description?.toString()?.trim().orEmpty()
        val rawExtrasTitle = desc.extras?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: desc.extras?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: desc.extras?.getString("android.media.metadata.TITLE")

        val title = when {
            rawTitle.isNotBlank() -> rawTitle
            !rawExtrasTitle.isNullOrBlank() -> rawExtrasTitle
            rawDesc.isNotBlank() && rawDesc != rawSubtitle -> rawDesc
            else -> rawTitle
        }

        val coverUri = desc.iconUri?.toString()
            ?: desc.iconBitmap?.let {
                saveBitmapToLocalFile(
                    bitmap = it,
                    prefix = QUEUE_ART_PREFIX,
                    maxRetained = 24,
                    trackKey = "${item.queueId}_${rawTitle}"
                )
            }

        return QueueTrackItem(
            id = item.queueId,
            mediaId = desc.mediaId,
            mediaUri = desc.mediaUri?.toString(),
            title = title,
            artist = rawSubtitle,
            album = rawDesc,
            coverUri = coverUri
        )
    }

    companion object {
        private const val MEDIA_ART_PREFIX = "media_art_"
        private const val QUEUE_ART_PREFIX = "queue_art_"

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
