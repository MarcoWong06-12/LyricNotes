package com.linernotes.app.core.floating

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import kotlin.math.roundToInt
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
import kotlinx.coroutines.flow.combine
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

    @Inject
    lateinit var aiPreferences: com.linernotes.app.core.preference.AiPreferences

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private var dragCurrentX: Float = 0f
    private var dragCurrentY: Float = 0f
    private var isViewAdded: Boolean = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    // 屏幕熄屏/亮屏广播监听：熄屏时挂起 Compose 渲染管线与 Marquee，极大降低后台音乐播放功耗
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> overlayLifecycleOwner.onScreenOff()
                Intent.ACTION_SCREEN_ON -> overlayLifecycleOwner.onScreenOn()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            _isRunningFlow.value = false
            floatingPreferences.isEnabled = false
            stopSelf()
            return
        }

        _isRunningFlow.value = true
        floatingPreferences.isEnabled = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(floatingPreferences.isLocked))

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

        // 监听前台/后台状态：在主应用处于前台且开启避让时自动隐藏悬浮窗，杜绝界面歌词与悬浮歌词双重重叠
        serviceScope.launch {
            combine(
                _isAppInForegroundFlow,
                floatingPreferences.hideWhenAppInForegroundFlow
            ) { inForeground, hideInForeground ->
                inForeground && hideInForeground
            }.collect { shouldHide ->
                composeView?.visibility = if (shouldHide) View.GONE else View.VISIBLE
            }
        }

        // 监听悬浮窗宽度动态变化：在安全视口范围内重新钳制，杜绝贴边时调整宽度飞出屏幕
        serviceScope.launch {
            floatingPreferences.capsuleWidthDpFlow.collect { widthDp ->
                windowLayoutParams?.let { params ->
                    val landscape = isLandscape()
                    val density = resources.displayMetrics.density
                    val viewWidth = (widthDp * density).toInt()
                    val viewHeight = composeView?.height?.takeIf { it > 0 } ?: (52 * density).toInt()
                    val bounds = getSafeDragBounds(viewWidth, viewHeight, landscape)
                    val screenBounds = getScreenBounds(landscape)

                    val savedX = floatingPreferences.getLastPositionX(landscape)
                    val newX = if (savedX < 0) {
                        ((screenBounds.width() - viewWidth) / 2).coerceIn(bounds.left, bounds.right)
                    } else {
                        params.x.coerceIn(bounds.left, bounds.right)
                    }
                    val clampedY = params.y.coerceIn(bounds.top, bounds.bottom)
                    if (newX != params.x || clampedY != params.y) {
                        params.x = newX
                        params.y = clampedY
                        dragCurrentX = newX.toFloat()
                        dragCurrentY = clampedY.toFloat()
                        try {
                            windowManager?.updateViewLayout(composeView, params)
                        } catch (e: Exception) {}
                    }
                }
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

    private fun isLandscape(): Boolean {
        val configOrientation = resources.configuration.orientation
        return if (configOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            true
        } else if (configOrientation == Configuration.ORIENTATION_PORTRAIT) {
            false
        } else {
            val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            val rawBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.bounds
            } else {
                val dm = resources.displayMetrics
                Rect(0, 0, dm.widthPixels, dm.heightPixels)
            }
            rawBounds.width() > rawBounds.height()
        }
    }

    private fun getScreenBounds(isLandscape: Boolean = isLandscape()): Rect {
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        val rawBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            val dm = resources.displayMetrics
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val w = if (isLandscape) maxOf(rawBounds.width(), rawBounds.height()) else minOf(rawBounds.width(), rawBounds.height())
        val h = if (isLandscape) minOf(rawBounds.width(), rawBounds.height()) else maxOf(rawBounds.width(), rawBounds.height())
        return Rect(0, 0, w, h)
    }

    private data class SafeInsets(val top: Int, val bottom: Int, val left: Int, val right: Int)

    /**
     * 动态查询系统状态栏、手势导航栏与居中打孔屏 Cutout 的真实安全边距
     * API 30+ 优先使用 WindowMetrics Insets，低版本平滑退行至标准 Dimen 测量
     */
    private fun getSystemBarInsets(): SafeInsets {
        val density = resources.displayMetrics.density
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            SafeInsets(
                top = insets.top.coerceAtLeast((24 * density).toInt()),
                bottom = insets.bottom.coerceAtLeast((16 * density).toInt()),
                left = insets.left,
                right = insets.right
            )
        } else {
            val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (statusId > 0) resources.getDimensionPixelSize(statusId) else (28 * density).toInt()
            val navId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navBarHeight = if (navId > 0) resources.getDimensionPixelSize(navId) else (44 * density).toInt()
            SafeInsets(
                top = statusBarHeight,
                bottom = navBarHeight,
                left = 0,
                right = 0
            )
        }
    }

    /**
     * 根据当前组件视口尺寸与系统 Insets 计算动态防碰撞拖拽包围盒
     */
    private fun getSafeDragBounds(viewWidth: Int, viewHeight: Int, isLandscape: Boolean = isLandscape()): Rect {
        val screenBounds = getScreenBounds(isLandscape)
        val insets = getSystemBarInsets()
        val density = resources.displayMetrics.density
        val marginPx = (8 * density).toInt()

        val minY = insets.top + marginPx
        val maxY = (screenBounds.height() - insets.bottom - viewHeight - marginPx).coerceAtLeast(minY)
        val minX = insets.left + marginPx
        val maxX = (screenBounds.width() - insets.right - viewWidth - marginPx).coerceAtLeast(minX)

        return Rect(minX, minY, maxX, maxY)
    }

    private fun initOverlayWindow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val landscape = isLandscape()
        val screenBounds = getScreenBounds(landscape)
        val density = resources.displayMetrics.density
        val insets = getSystemBarInsets()
        val viewWidth = (floatingPreferences.capsuleWidthDp * density).toInt()
        val viewHeight = (52 * density).toInt()
        val bounds = getSafeDragBounds(viewWidth, viewHeight, landscape)

        val savedX = floatingPreferences.getLastPositionX(landscape)
        val savedY = floatingPreferences.getLastPositionY(landscape)

        // 安全双轴钳制：初次或历史坐标必须被严格限定在安全边距内，杜绝横竖屏切换后悬浮窗飞出屏幕失踪
        val initialX = if (savedX >= 0) {
            savedX.coerceIn(bounds.left, bounds.right)
        } else {
            ((screenBounds.width() - viewWidth) / 2).coerceIn(bounds.left, bounds.right)
        }
        val initialY = if (savedY >= 0) {
            savedY.coerceIn(bounds.top, bounds.bottom)
        } else {
            val defaultMarginTop = if (landscape) 16 else 24
            (insets.top + (defaultMarginTop * density).toInt()).coerceIn(bounds.top, bounds.bottom)
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 启用硬件加速 FLAG_HARDWARE_ACCELERATED，保证在高刷屏 (90Hz/120Hz) 上 Compose 渲染管线流畅不掉帧
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

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
                val appLanguageCode by aiPreferences.appLanguageFlow.collectAsState()
                val strings = remember(appLanguageCode) {
                    com.linernotes.app.core.i18n.resolveAppStrings(appLanguageCode)
                }

                androidx.compose.runtime.CompositionLocalProvider(com.linernotes.app.core.i18n.LocalStrings provides strings) {
                    val nowData by nowPlayingRepository.nowPlayingData.collectAsState()
                    val isLocked by floatingPreferences.isLockedFlow.collectAsState()
                    val isBilingual by floatingPreferences.isBilingualFlow.collectAsState()
                    val bgAlpha by floatingPreferences.backgroundAlphaFlow.collectAsState()
                    val fontScale by floatingPreferences.fontScaleFlow.collectAsState()
                    val textColor by floatingPreferences.textColorFlow.collectAsState()
                    val displayMode by floatingPreferences.displayModeFlow.collectAsState()
                    val capsuleWidthDp by floatingPreferences.capsuleWidthDpFlow.collectAsState()
                    val floatingStyle by floatingPreferences.floatingStyleFlow.collectAsState()
                    val isTraditional by aiPreferences.isTraditionalChineseFlow.collectAsState()
                    val textAlignment by floatingPreferences.textAlignmentFlow.collectAsState()

                    FloatingLyricsCapsule(
                        nowPlayingData = nowData,
                        isLocked = isLocked,
                        isBilingual = isBilingual,
                        backgroundAlpha = bgAlpha,
                        fontScale = fontScale,
                        textColor = textColor,
                        displayMode = displayMode,
                        floatingStyle = floatingStyle,
                        capsuleWidthDp = capsuleWidthDp,
                        isTraditional = isTraditional,
                        textAlignment = textAlignment,
                        onDragStart = {
                            cancelSnapAnimation()
                            windowLayoutParams?.let { params ->
                                dragCurrentX = params.x.toFloat()
                                dragCurrentY = params.y.toFloat()
                            }
                        },
                        onDrag = { dx, dy ->
                            cancelSnapAnimation()
                            windowLayoutParams?.let { params ->
                                val densityVal = resources.displayMetrics.density
                                val viewHeight = composeView?.height?.takeIf { it > 0 } ?: (52 * densityVal).toInt()
                                val viewWidth = composeView?.width?.takeIf { it > 0 } ?: (capsuleWidthDp * densityVal).toInt()
                                val bounds = getSafeDragBounds(viewWidth, viewHeight)

                                // 采用高精度浮点累积，彻底消除 90/120Hz 刷新率下的慢速拖拽丢帧与卡滞
                                dragCurrentX = (dragCurrentX + dx).coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
                                dragCurrentY = (dragCurrentY + dy).coerceIn(bounds.top.toFloat(), bounds.bottom.toFloat())

                                val newX = dragCurrentX.roundToInt()
                                val newY = dragCurrentY.roundToInt()
                                if (newX != params.x || newY != params.y) {
                                    params.x = newX
                                    params.y = newY
                                    try {
                                        windowManager?.updateViewLayout(this@apply, params)
                                    } catch (e: Exception) {}
                                }
                            }
                        },
                        onDragEnd = { isExpanded ->
                            windowLayoutParams?.let { params ->
                                cancelSnapAnimation()
                                // 自由悬浮停留：单次原子持久化坐标（按横竖屏独立存储）
                                floatingPreferences.setLastPosition(params.x, params.y, isLandscape())
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
                        onOpenApp = {
                            val intent = Intent(this@FloatingLyricsService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                        },
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, windowLayoutParams)
            isViewAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
            isViewAdded = false
            stopSelf()
        }
    }

    private fun cancelSnapAnimation() {
        snapAnimator?.cancel()
        snapAnimator = null
    }

    private fun handleExpandChanged(isExpanded: Boolean) {
        windowLayoutParams?.let { params ->
            val landscape = isLandscape()
            val density = resources.displayMetrics.density
            val expandedWidthPx = (EXPANDED_WIDTH_DP * density).toInt()
            val expandedHeightPx = (270 * density).toInt()
            val bounds = getSafeDragBounds(expandedWidthPx, expandedHeightPx, landscape)

            cancelSnapAnimation()
            if (isExpanded) {
                // 展开时双轴安全视口钳制，确保右侧与底端播控栏 100% 完整显示在屏幕可视区域内并动态避让状态栏/导航栏
                val targetX = params.x.coerceIn(bounds.left, bounds.right)
                val targetY = params.y.coerceIn(bounds.top, bounds.bottom)
                if (targetX != params.x || targetY != params.y) {
                    params.x = targetX
                    params.y = targetY
                    try {
                        windowManager?.updateViewLayout(composeView, params)
                    } catch (e: Exception) {}
                }
            } else {
                floatingPreferences.setLastPosition(params.x, params.y, landscape)
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        cancelSnapAnimation()
        val landscape = isLandscape()
        val screenBounds = getScreenBounds(landscape)
        val density = resources.displayMetrics.density
        val viewWidth = composeView?.width?.takeIf { it > 0 } ?: (floatingPreferences.capsuleWidthDp * density).toInt()
        val viewHeight = composeView?.height?.takeIf { it > 0 } ?: (48 * density).toInt()
        val bounds = getSafeDragBounds(viewWidth, viewHeight, landscape)
        val currentX = params.x
        val targetX = if (currentX + (viewWidth / 2) < screenBounds.width() / 2) {
            bounds.left // 吸附至左侧安全边距
        } else {
            bounds.right // 吸附至右侧安全边距
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

        floatingPreferences.setLastPosition(targetX, params.y, landscape)
    }

    private fun updateLockState(locked: Boolean) {
        val wm = windowManager ?: return
        val cv = composeView ?: return
        val lp = windowLayoutParams ?: return

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

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
                "LinerNotes 桌面悬浮歌词",
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
            .setContentTitle("LinerNotes 桌面歌词已开启")
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
            val isLandscape = when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> true
                Configuration.ORIENTATION_PORTRAIT -> false
                else -> resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }

            val density = resources.displayMetrics.density
            val currentViewWidth = composeView?.width?.takeIf { it > 0 } ?: (floatingPreferences.capsuleWidthDp * density).toInt()
            val currentViewHeight = composeView?.height?.takeIf { it > 0 } ?: (48 * density).toInt()
            val bounds = getSafeDragBounds(currentViewWidth, currentViewHeight, isLandscape)
            val screenBounds = getScreenBounds(isLandscape)
            val insets = getSystemBarInsets()

            val savedX = floatingPreferences.getLastPositionX(isLandscape)
            val savedY = floatingPreferences.getLastPositionY(isLandscape)

            // 彻底解决横竖屏旋转位置错位与锁定后换回横屏偏左不居中问题：
            // 横屏与竖屏采用独立记忆坐标；若横屏下未曾手动拖拽，自动对齐横屏水平居中位置，拒绝因竖屏钳制导致横屏偏左
            val targetX = if (savedX >= 0) {
                savedX.coerceIn(bounds.left, bounds.right)
            } else {
                ((screenBounds.width() - currentViewWidth) / 2).coerceIn(bounds.left, bounds.right)
            }

            val targetY = if (savedY >= 0) {
                savedY.coerceIn(bounds.top, bounds.bottom)
            } else {
                val defaultMarginTop = if (isLandscape) 16 else 24
                (insets.top + (defaultMarginTop * density).toInt()).coerceIn(bounds.top, bounds.bottom)
            }

            params.x = targetX
            params.y = targetY
            dragCurrentX = targetX.toFloat()
            dragCurrentY = targetY.toFloat()

            try {
                windowManager?.updateViewLayout(composeView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户从最近任务划掉主应用时，确保重置前台标记，避免悬浮窗在桌面上永久不可见
        setAppInForeground(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunningFlow.value = false
        floatingPreferences.isEnabled = false
        cancelSnapAnimation()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {}
        serviceScope.cancel()

        composeView?.let { view ->
            if (isViewAdded) {
                try {
                    view.disposeComposition()
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isViewAdded = false
            }
        }
        overlayLifecycleOwner.onDestroy()
        composeView = null
        windowManager = null

        // 彻底清除前台通知，避免某些定制 ROM 残留幽灵常驻条
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_ID)
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

        fun onScreenOff() {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
        }

        fun onScreenOn() {
            if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
            if (lifecycleRegistry.currentState < Lifecycle.State.RESUMED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }

        fun onDestroy() {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    companion object {
        const val EXPANDED_WIDTH_DP = 356
        const val COMPACT_WIDTH_DP = 356

        const val CHANNEL_ID = "floating_lyrics_channel"
        const val NOTIFICATION_ID = 20001

        const val ACTION_TOGGLE_LOCK = "com.linernotes.app.action.TOGGLE_LOCK"
        const val ACTION_STOP_SERVICE = "com.linernotes.app.action.STOP_FLOATING_SERVICE"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        val isServiceRunning: Boolean
            get() = _isRunningFlow.value

        private val _isAppInForegroundFlow = MutableStateFlow(false)
        val isAppInForegroundFlow: StateFlow<Boolean> = _isAppInForegroundFlow.asStateFlow()

        fun setAppInForeground(inForeground: Boolean) {
            _isAppInForegroundFlow.value = inForeground
        }

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
