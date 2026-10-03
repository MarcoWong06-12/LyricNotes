package com.linernotes.app.core.floating

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.linernotes.app.MainActivity
import com.linernotes.app.R
import com.linernotes.app.core.playback.PlaybackStateManager
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.data.repository.NowPlayingRepository
import com.linernotes.app.presentation.floating.FloatingLyricsCapsule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 桌面悬浮歌词核心前台服务 (Floating Lyrics Overlay Service)
 * 宿主在 WindowManager 之上，通过 ComposeView 提供 120fps 现代响应式流体界面
 */
@AndroidEntryPoint
class FloatingLyricsService : Service() {

    @Inject
    lateinit var nowPlayingRepository: NowPlayingRepository

    @Inject
    lateinit var floatingPreferences: FloatingLyricsPreferences

    @Inject
    lateinit var playbackStateManager: PlaybackStateManager

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunningFlow.value = true
        floatingPreferences.isEnabled = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(floatingPreferences.isLocked))

        overlayLifecycleOwner.onCreate()
        initOverlayWindow()

        // 监听锁定状态变化，动态调整 WindowManager 触摸穿透 Flag
        serviceScope.launch {
            floatingPreferences.isLockedFlow.collect { locked ->
                updateLockState(locked)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(locked))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_LOCK -> {
                floatingPreferences.isLocked = !floatingPreferences.isLocked
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun initOverlayWindow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val displayMetrics = resources.displayMetrics
        val initialX = if (floatingPreferences.lastPositionX >= 0) {
            floatingPreferences.lastPositionX
        } else {
            (displayMetrics.widthPixels - 700) / 2
        }
        val initialY = floatingPreferences.lastPositionY.coerceIn(80, displayMetrics.heightPixels - 200)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val initialFlags = if (floatingPreferences.isLocked) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }

        windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setContent {
                val nowData by nowPlayingRepository.nowPlayingData.collectAsState()
                val isLocked by floatingPreferences.isLockedFlow.collectAsState()
                val isBilingual by floatingPreferences.isBilingualFlow.collectAsState()
                val bgAlpha by floatingPreferences.backgroundAlphaFlow.collectAsState()
                val fontScale by floatingPreferences.fontScaleFlow.collectAsState()

                FloatingLyricsCapsule(
                    nowPlayingData = nowData,
                    isLocked = isLocked,
                    isBilingual = isBilingual,
                    backgroundAlpha = bgAlpha,
                    fontScale = fontScale,
                    onDragStart = {
                        cancelSnapAnimation()
                    },
                    onDrag = { dx, dy ->
                        cancelSnapAnimation()
                        windowLayoutParams?.let { params ->
                            val metrics = resources.displayMetrics
                            val density = metrics.density
                            val minY = (36 * density).toInt()
                            val viewHeight = composeView?.height ?: (48 * density).toInt()
                            val maxY = (metrics.heightPixels - viewHeight - (48 * density).toInt()).coerceAtLeast(minY)

                            params.x += dx.toInt()
                            params.y = (params.y + dy.toInt()).coerceIn(minY, maxY)
                            windowManager?.updateViewLayout(this@apply, params)
                        }
                    },
                    onDragEnd = {
                        windowLayoutParams?.let { params ->
                            snapToNearestEdge(params)
                        }
                    },
                    onExpandChanged = { isExpanded ->
                        handleExpandChanged(isExpanded)
                    },
                    onToggleLock = {
                        floatingPreferences.isLocked = !floatingPreferences.isLocked
                    },
                    onToggleBilingual = {
                        floatingPreferences.isBilingual = !floatingPreferences.isBilingual
                    },
                    onPrevious = { playbackStateManager.skipToPrevious() },
                    onPlayPause = { playbackStateManager.togglePlayPause() },
                    onNext = { playbackStateManager.skipToNext() },
                    onClose = { stopSelf() }
                )
            }
        }

        try {
            windowManager?.addView(composeView, windowLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun cancelSnapAnimation() {
        snapAnimator?.cancel()
        snapAnimator = null
    }

    private fun handleExpandChanged(isExpanded: Boolean) {
        windowLayoutParams?.let { params ->
            val metrics = resources.displayMetrics
            val density = metrics.density
            val marginPx = (16 * density).toInt()
            val expandedWidthPx = (340 * density).toInt()

            // 判断胶囊当前是否位于屏幕右侧半区
            val isAnchoredRight = params.x + (composeView?.width ?: 0) / 2 > metrics.widthPixels / 2
            if (isAnchoredRight) {
                cancelSnapAnimation()
                if (isExpanded) {
                    // 向左平移避免卡片右边缘按钮被裁切
                    params.x = (metrics.widthPixels - expandedWidthPx - marginPx).coerceAtLeast(marginPx)
                    try {
                        windowManager?.updateViewLayout(composeView, params)
                    } catch (e: Exception) {}
                } else {
                    snapToNearestEdge(params)
                }
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        cancelSnapAnimation()
        val metrics = resources.displayMetrics
        val density = metrics.density
        val marginPx = (16 * density).toInt()
        val viewWidth = composeView?.width ?: (260 * density).toInt()
        val currentX = params.x
        val targetX = if (currentX + (viewWidth / 2) < metrics.widthPixels / 2) {
            marginPx // 吸附至左侧安全边距
        } else {
            (metrics.widthPixels - viewWidth - marginPx).coerceAtLeast(marginPx) // 吸附至右侧安全边距
        }

        val startX = currentX
        snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                params.x = va.animatedValue as Int
                try {
                    windowManager?.updateViewLayout(composeView, params)
                } catch (e: Exception) {}
            }
            start()
        }

        floatingPreferences.lastPositionX = targetX
        floatingPreferences.lastPositionY = params.y
    }

    private fun updateLockState(locked: Boolean) {
        val wm = windowManager ?: return
        val cv = composeView ?: return
        val lp = windowLayoutParams ?: return

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        lp.flags = if (locked) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }

        try {
            wm.updateViewLayout(cv, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LyricNotes 桌面悬浮歌词",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持桌面悬浮歌词在后台稳定运行，并提供锁定/解锁快捷控制"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isLocked: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val toggleLockIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = ACTION_TOGGLE_LOCK
        }.let {
            PendingIntent.getService(this, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val closeIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }.let {
            PendingIntent.getService(this, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val lockActionTitle = if (isLocked) "🔓 解除锁定" else "🔒 穿透锁定"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LyricNotes 桌面歌词已开启")
            .setContentText(if (isLocked) "当前处于触摸穿透锁定状态" else "轻触可拖拽或展开播控卡片")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, lockActionTitle, toggleLockIntent)
            .addAction(0, "关闭悬浮窗", closeIntent)
            .build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowLayoutParams?.let { params ->
            val metrics = resources.displayMetrics
            val density = metrics.density
            val marginPx = (16 * density).toInt()
            val currentViewWidth = composeView?.width ?: (260 * density).toInt()
            val currentViewHeight = composeView?.height ?: (48 * density).toInt()

            params.x = params.x.coerceIn(marginPx, (metrics.widthPixels - currentViewWidth - marginPx).coerceAtLeast(marginPx))
            val minY = (36 * density).toInt()
            val maxY = (metrics.heightPixels - currentViewHeight - (48 * density).toInt()).coerceAtLeast(minY)
            params.y = params.y.coerceIn(minY, maxY)

            try {
                windowManager?.updateViewLayout(composeView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunningFlow.value = false
        floatingPreferences.isEnabled = false
        cancelSnapAnimation()
        serviceScope.cancel()
        overlayLifecycleOwner.onDestroy()

        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        composeView = null
        windowManager = null
    }

    /**
     * 自定义轻量级 LifecycleOwner 与 SavedStateRegistryOwner，为 ComposeView 在 WindowManager 中提供完整的生命周期环境
     */
    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun onCreate() {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    companion object {
        const val CHANNEL_ID = "floating_lyrics_channel"
        const val NOTIFICATION_ID = 20001

        const val ACTION_TOGGLE_LOCK = "com.linernotes.app.action.TOGGLE_LOCK"
        const val ACTION_STOP_SERVICE = "com.linernotes.app.action.STOP_FLOATING_SERVICE"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        val isServiceRunning: Boolean
            get() = _isRunningFlow.value

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.stopService(intent)
        }

        fun requestOverlayPermission(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
