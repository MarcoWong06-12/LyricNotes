package com.linernotes.app.core.playback

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 基于 Android NotificationListenerService 与 MediaSessionManager 的核心媒体同步服务
 * 实时捕获 Spotify 及主流流媒体应用的曲目信息、专辑封面、播放状态与毫秒级进度
 */
class MediaPlaybackSyncService : NotificationListenerService(), MediaControlActionHandler {

    private data class MonitoredSession(
        val controller: MediaController,
        val callback: MediaController.Callback,
        var lastState: Int
    )

    private var mediaSessionManager: MediaSessionManager? = null
    @Volatile
    private var activeController: MediaController? = null
    @Volatile
    private var activeCompatController: MediaControllerCompat? = null
    private val monitoredSessions = LinkedHashMap<MediaSession.Token, MonitoredSession>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val playbackStateManager by lazy { PlaybackStateManager.get(this) }
    private var ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var queueSyncJob: Job? = null
    private val spotifyReceiver = SpotifyBroadcastReceiver()
    private var isSpotifyReceiverRegistered = false

    private fun scheduleQueueSync(
        queue: List<MediaSessionCompat.QueueItem>?,
        controllerSnapshot: MediaController?
    ) {
        queueSyncJob?.cancel()
        val targetToken = controllerSnapshot?.sessionToken ?: return
        if (queue.isNullOrEmpty()) {
            if (activeController?.sessionToken == targetToken) {
                playbackStateManager.updateQueueItems(emptyList())
            }
            return
        }
        val maxQueueArtRetained = computeQueueCoverRetention(queue.size)
        queueSyncJob = ioScope.launch {
            val items = queue.map { mapQueueItem(it, maxQueueArtRetained) }
            if (activeController?.sessionToken == targetToken) {
                playbackStateManager.updateQueueItems(items)
            }
        }
    }

    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) {
            val isShuffle = shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
            playbackStateManager.updateShuffleMode(isShuffle)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                else -> 0
            }
            playbackStateManager.updateRepeatMode(mapped)
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            scheduleQueueSync(queue, activeController)
        }
    }

    private fun createControllerCallback(controller: MediaController): MediaController.Callback {
        return object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state ?: return
                val session = monitoredSessions[controller.sessionToken]
                val prevState = session?.lastState ?: PlaybackState.STATE_NONE
                session?.lastState = state.state

                val active = activeController
                if (controller.sessionToken == active?.sessionToken) {
                    if (state.state != PlaybackState.STATE_PLAYING) {
                        val playingAlternative = monitoredSessions.values
                            .firstOrNull {
                                it.controller.sessionToken != controller.sessionToken &&
                                    it.controller.playbackState?.state == PlaybackState.STATE_PLAYING
                            }
                            ?.controller
                        if (playingAlternative != null) {
                            attachToController(playingAlternative)
                            return
                        }
                    }
                    handlePlaybackStateUpdate(state)
                } else if (state.state == PlaybackState.STATE_PLAYING) {
                    val activeIsPlaying = active?.playbackState?.state == PlaybackState.STATE_PLAYING
                    if (!activeIsPlaying || prevState != PlaybackState.STATE_PLAYING) {
                        attachToController(controller, initialPlaybackState = state)
                    }
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata ?: return
                val active = activeController
                if (controller.sessionToken == active?.sessionToken) {
                    handleMetadataUpdate(metadata, controller)
                } else if (active == null ||
                    (active.playbackState?.state != PlaybackState.STATE_PLAYING &&
                        controller.playbackState?.state == PlaybackState.STATE_PLAYING)
                ) {
                    attachToController(controller, initialMetadata = metadata)
                }
            }

            override fun onSessionDestroyed() {
                super.onSessionDestroyed()
                monitoredSessions.remove(controller.sessionToken)?.let { removed ->
                    try {
                        removed.controller.unregisterCallback(removed.callback)
                    } catch (_: Exception) {
                    }
                }
                if (controller.sessionToken == activeController?.sessionToken) {
                    detachActiveCompatController()
                    activeController = null
                    findAndAttachActiveController()
                }
            }
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
        if (!isSpotifyReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(SpotifyBroadcastReceiver.ACTION_METADATA_CHANGED)
                    addAction(SpotifyBroadcastReceiver.ACTION_PLAYBACK_STATE_CHANGED)
                }
                ContextCompat.registerReceiver(
                    this,
                    spotifyReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isSpotifyReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, MediaPlaybackSyncService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                componentName,
                mainHandler
            )
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            inspectAndAttachController(controllers)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanupSessionListenersAndJobs()
    }

    override fun onDestroy() {
        cleanupSessionListenersAndJobs()
        super.onDestroy()
    }

    private fun cleanupSessionListenersAndJobs() {
        playbackStateManager.unregisterControlHandler(this)
        playbackStateManager.refreshNotificationAccess(this)
        queueSyncJob?.cancel()
        queueSyncJob = null
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        if (isSpotifyReceiverRegistered) {
            try {
                unregisterReceiver(spotifyReceiver)
            } catch (_: Exception) {
            }
            isSpotifyReceiverRegistered = false
        }
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        detachActiveCompatController()
        monitoredSessions.values.forEach { session ->
            try {
                session.controller.unregisterCallback(session.callback)
            } catch (_: Exception) {
            }
        }
        monitoredSessions.clear()
        activeController = null
    }

    private fun detachActiveCompatController() {
        try {
            activeCompatController?.unregisterCallback(compatCallback)
        } catch (_: Exception) {
        }
        activeCompatController = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val pkg = sbn?.packageName ?: return
        val current = playbackStateManager.playbackState.value

        val isMusic = isMusicApp(pkg, current.packageName)

        if (isMusic && (activeController == null || activeController?.packageName != pkg)) {
            findAndAttachActiveController()
        }

        val matchesActivePackage = pkg == current.packageName || pkg == activeController?.packageName
        if (isMusic && current.hasValidTrack && matchesActivePackage) {
            val expectedTitle = current.title
            val expectedArtist = current.artist
            val expectedEpoch = current.trackEpoch
            if (isCoverUrlValid(current.coverUrl)) return

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
                            prefix = MEDIA_ART_NOTIF_PREFIX,
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
                val tempFile = File(cacheDir, "${prefix}${keyHash}_${coverSequence.incrementAndGet()}.tmp")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                if (!tempFile.renameTo(artFile)) {
                    tempFile.copyTo(artFile, overwrite = true)
                    tempFile.delete()
                }
            } else {
                artFile.setLastModified(System.currentTimeMillis())
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
            val files = cacheDir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".png") } ?: return
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
        val musicControllers = controllers.orEmpty().filter { isMusicApp(it.packageName) }
        val activeTokens = musicControllers.mapTo(HashSet()) { it.sessionToken }

        val removedTokens = monitoredSessions.keys.filter { it !in activeTokens }
        removedTokens.forEach { token ->
            monitoredSessions.remove(token)?.let { removed ->
                try {
                    removed.controller.unregisterCallback(removed.callback)
                } catch (_: Exception) {
                }
            }
        }

        musicControllers.forEach { controller ->
            if (!monitoredSessions.containsKey(controller.sessionToken)) {
                val cb = createControllerCallback(controller)
                try {
                    controller.registerCallback(cb, mainHandler)
                    monitoredSessions[controller.sessionToken] = MonitoredSession(
                        controller = controller,
                        callback = cb,
                        lastState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
                    )
                } catch (_: Exception) {
                }
            }
        }

        if (musicControllers.isEmpty()) {
            detachActiveCompatController()
            activeController = null
            queueSyncJob?.cancel()
            queueSyncJob = null
            playbackStateManager.updatePlaybackStateAtomically { current ->
                if (!current.isPlaying) {
                    current
                } else {
                    val now = SystemClock.elapsedRealtime()
                    current.copy(
                        isPlaying = false,
                        currentPositionMs = current.getEstimatedPositionMs(now),
                        lastUpdateTimeMs = now
                    )
                }
            }
            return
        }

        val currentActiveToken = activeController?.sessionToken
        val currentActiveInList = musicControllers.firstOrNull { it.sessionToken == currentActiveToken }
        val playingControllers = musicControllers.filter {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        val chosen = when {
            playingControllers.isNotEmpty() -> {
                playingControllers.firstOrNull { it.sessionToken == currentActiveToken }
                    ?: playingControllers.firstOrNull { it.packageName == SpotifyBroadcastReceiver.SPOTIFY_PACKAGE }
                    ?: playingControllers.first()
            }
            currentActiveInList != null -> currentActiveInList
            else -> {
                musicControllers.firstOrNull { it.packageName == SpotifyBroadcastReceiver.SPOTIFY_PACKAGE }
                    ?: musicControllers.first()
            }
        }

        if (chosen.sessionToken != currentActiveToken) {
            attachToController(chosen)
        }
    }

    private fun attachToController(
        chosen: MediaController,
        initialMetadata: MediaMetadata? = chosen.metadata,
        initialPlaybackState: PlaybackState? = chosen.playbackState
    ) {
        detachActiveCompatController()
        activeController = chosen

        try {
            val tokenCompat = MediaSessionCompat.Token.fromToken(chosen.sessionToken)
            val compat = MediaControllerCompat(this, tokenCompat)
            activeCompatController = compat
            compat.registerCallback(compatCallback, mainHandler)

            val initShuffle = compat.shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
            val initRepeat = when (compat.repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                else -> 0
            }
            val rawQueue = compat.queue

            playbackStateManager.updatePlaybackModes(initShuffle, initRepeat)
            scheduleQueueSync(rawQueue, chosen)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        initialMetadata?.let { handleMetadataUpdate(it, chosen) }
        initialPlaybackState?.let { handlePlaybackStateUpdate(it) }
    }

    private fun handleMetadataUpdate(
        metadata: MediaMetadata,
        sourceController: MediaController? = activeController
    ) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        if (title.isBlank()) return

        val candidateUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: metadata.description?.iconUri?.toString()

        val immediateUri = when {
            !candidateUri.isNullOrBlank() && (
                candidateUri.startsWith("http://") ||
                    candidateUri.startsWith("https://") ||
                    candidateUri.startsWith("file://")
                ) -> candidateUri
            else -> null
        }

        val pkg = sourceController?.packageName ?: ""
        val sourceApp = MediaSourceApp.fromPackageName(pkg)

        val updated = playbackStateManager.updateMetadataAndCover(
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            coverUrl = immediateUri,
            coverQuality = CoverQuality.NATIVE_METADATA,
            packageName = pkg,
            sourceApp = sourceApp,
            existingCoverValidator = ::isCoverUrlValid
        )

        val needsAsyncBitmapCover = immediateUri.isNullOrBlank() &&
            playbackStateManager.canAcceptCover(
                expectedTitle = title,
                expectedArtist = artist,
                quality = CoverQuality.NATIVE_METADATA,
                expectedEpoch = updated.trackEpoch,
                existingCoverValidator = ::isCoverUrlValid
            )

        if (needsAsyncBitmapCover) {
            val trackKey = "${artist}_${title}"
            val compatMeta = if (sourceController?.sessionToken == activeController?.sessionToken) {
                activeCompatController?.metadata
            } else {
                null
            }
            val rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.description?.iconBitmap
                ?: compatMeta?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                ?: compatMeta?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
                ?: compatMeta?.description?.iconBitmap
            val bitmapCopy = rawBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                try { bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { bmp }
            }

            if (bitmapCopy != null || (!candidateUri.isNullOrBlank() && candidateUri.startsWith("content://"))) {
                val expectedEpoch = updated.trackEpoch
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

    private fun handlePlaybackStateUpdate(state: PlaybackState) {
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        val position = state.position.takeIf { it >= 0L }
        val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
        val now = SystemClock.elapsedRealtime()
        val anchorTimeMs = state.lastPositionUpdateTime
            .takeIf { it > 0L && it <= now + 1_000L }
            ?: now

        playbackStateManager.updatePlaybackClock(
            positionMs = position,
            isPlaying = isPlaying,
            playbackSpeed = speed,
            lastUpdateTimeMs = anchorTimeMs
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
        playbackStateManager.updateShuffleMode(targetMode != PlaybackStateCompat.SHUFFLE_MODE_NONE)
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
        playbackStateManager.updateRepeatMode(mappedMode)
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

    private fun mapQueueItem(
        item: MediaSessionCompat.QueueItem,
        maxRetained: Int = MIN_QUEUE_ART_RETAINED
    ): QueueTrackItem {
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
                    maxRetained = maxRetained,
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

    private fun isCoverUrlValid(coverUrl: String?): Boolean {
        if (coverUrl.isNullOrBlank()) return false
        return !coverUrl.startsWith("/") || File(coverUrl).exists()
    }

    companion object {
        private const val MEDIA_ART_PREFIX = "media_art_meta_"
        private const val MEDIA_ART_NOTIF_PREFIX = "media_art_notif_"
        private const val QUEUE_ART_PREFIX = "queue_art_"
        private const val MIN_QUEUE_ART_RETAINED = 24

        private val KNOWN_MUSIC_PACKAGES = setOf(
            "com.spotify.music",
            "com.spotify.lite",
            "com.apple.android.music",
            "com.apple.android.music.classical",
            "com.netease.cloudmusic",
            "com.netease.cloudmusic.lite",
            "com.tencent.qqmusic",
            "com.tencent.qqmusicpad",
            "com.tencent.qqmusic.lite",
            "com.kugou.android",
            "com.kugou.android.lite",
            "com.kugou.android.elder",
            "cn.kuwo.player",
            "cn.kuwo.kwmusiccar",
            "com.google.android.apps.youtube.music",
            "com.aspiro.tidal",
            "deezer.android.app",
            "com.soundcloud.android",
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.bilibili.app.blue",
            "tv.danmaku.bilibilihd",
            "com.maxmpz.audioplayer",
            "tv.plex.labs.plexamp",
            "com.neutroncode.mp",
            "com.luna.music",
            "cmccwm.mobilemusic",
            "com.amazon.mp3",
            "com.qobuz.music",
            "com.bandcamp.android",
            "com.pandora.android",
            "com.foobar2000.foobar2000",
            "com.aimp.player",
            "in.krosbits.musicolet",
            "com.salt.music",
            "com.hiby.music",
            "com.fiio.music",
            "com.shanling.eddictplayer",
            "com.piyush.music",
            "org.akanework.gramophone",
            "org.oxycblt.auxio",
            "code.name.monkey.retromusic",
            "com.dd3boh.outertune",
            "com.zionhuang.music",
            "com.miui.player",
            "com.samsung.android.app.music",
            "com.sec.android.app.music",
            "com.heytap.music",
            "com.oppo.music",
            "com.vivo.music",
            "com.android.mediacenter",
            "com.meizu.media.music",
            "com.sonyericsson.music",
            "com.tencent.ibg.joox",
            "com.skysoft.kkbox.android",
            "com.iloen.melon",
            "com.neowiz.android.bugs",
            "com.ktmusic.geniemusic",
            "skplanet.musicmate",
            "jp.linecorp.linemusic.android",
            "jp.awa.android",
            "ru.yandex.music",
            "com.audiomack"
        )

        private val EXCLUDED_PACKAGE_TOKENS = listOf(
            "browser",
            "chrome",
            "firefox",
            "webview",
            "opera",
            "sbrowser",
            "emmx",
            "ucmobile",
            "quark",
            "brave",
            "vivaldi",
            "duckduckgo",
            "alarm",
            "clock",
            "timer",
            "ringtone",
            "telecom",
            "dialer",
            "incall",
            "calling",
            "voip",
            "whatsapp",
            "telegram",
            "discord",
            "zoom",
            "teams",
            "skype",
            "tencent.mm",
            "systemui",
            "tts",
            "recorder",
            "screencast",
            "video",
            "kmplayer",
            "mxtech",
            "netflix",
            "disney",
            "tiktok",
            "camera",
            "gallery"
        )

        private val MUSIC_KEYWORD_TOKENS = listOf(
            "music",
            "audio",
            "player",
            "mp3",
            "flac",
            "hifi"
        )

        internal fun computeQueueCoverRetention(queueSize: Int): Int =
            maxOf(MIN_QUEUE_ART_RETAINED, queueSize + 4)

        internal fun isMusicApp(
            packageName: String?,
            currentPackageName: String? = null
        ): Boolean {
            if (packageName.isNullOrBlank()) return false
            val normalized = packageName.lowercase(Locale.ROOT)
            if (normalized in KNOWN_MUSIC_PACKAGES) return true
            if (EXCLUDED_PACKAGE_TOKENS.any { normalized.contains(it) }) return false
            if (!currentPackageName.isNullOrBlank() &&
                normalized == currentPackageName.lowercase(Locale.ROOT)
            ) {
                return true
            }
            return MUSIC_KEYWORD_TOKENS.any { normalized.contains(it) }
        }

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
