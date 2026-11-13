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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.resolveAppStrings
import com.linernotes.app.core.playback.PlaybackStateManager
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.preference.FloatingLyricsPreferences
import com.linernotes.app.data.repository.NowPlayingRepository
import com.linernotes.app.presentation.common.applyMaxRefreshRateToLayoutParams
import com.linernotes.app.presentation.floating.FloatingLyricsCapsule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(floatingPreferences.isLocked))

        if (!Settings.canDrawOverlays(this)) {
            _isRunningFlow.value = false
            floatingPreferences.isEnabled = false
            FloatingLyricsTileService.requestSync(this)
            stopSelf()
            return
        }

        _isRunningFlow.value = true
        floatingPreferences.isEnabled = true
        FloatingLyricsTileService.requestSync(this)

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        overlayLifecycleOwner.onCreate()
        initOverlayWindow()

        // 监听锁定状态变化，动态调整 WindowManager 触摸穿透 Flag
        serviceScope.launch {
            floatingPreferences.isLockedFlow.collect { locked ->
                updateLockState(locked)
                refreshNotification(locked)
            }
        }

        // 语言切换时即时更新通知渠道名称与通知文案
        serviceScope.launch {
            aiPreferences.appLanguageFlow.drop(1).collect {
                createNotificationChannel()
                refreshNotification(floatingPreferences.isLocked)
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

        // 监听悬浮窗宽度动态变化：同步固定 WindowManager 窗口宽度并在安全视口范围内重新钳制
        serviceScope.launch {
            floatingPreferences.capsuleWidthDpFlow.collect { widthDp ->
                windowLayoutParams?.let { params ->
                    val landscape = isLandscape()
                    val density = resources.displayMetrics.density
                    val viewWidth = getSafeOverlayWidthPx(widthDp, landscape)
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
                    if (viewWidth != params.width || newX != params.x || clampedY != params.y) {
                        params.width = viewWidth
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

    private fun getSafeOverlayWidthPx(
        widthDp: Int = floatingPreferences.capsuleWidthDp,
        isLandscape: Boolean = isLandscape()
    ): Int {
        val density = resources.displayMetrics.density
        val screenWidthPx = getScreenBounds(isLandscape).width()
        val insets = getSystemBarInsets()
        val sideMarginPx = (8 * density).toInt()
        val maxWidthPx = (screenWidthPx - insets.left - insets.right - sideMarginPx * 2)
            .coerceAtLeast((220 * density).toInt())
        val minWidthPx = (220 * density).toInt().coerceAtMost(maxWidthPx)
        return (widthDp * density).toInt().coerceIn(minWidthPx, maxWidthPx)
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
        val viewWidth = getSafeOverlayWidthPx(floatingPreferences.capsuleWidthDp, landscape)
        val viewHeight = (52 * density).toInt()
        val bounds = getSafeDragBounds(viewWidth, viewHeight, landscape)

        val savedX = floatingPreferences.getLastPositionX(landscape)
        val savedY = floatingPreferences.getLastPositionY(landscape)

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

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val initialFlags = if (floatingPreferences.isLocked) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }

        dragCurrentX = initialX.toFloat()
        dragCurrentY = initialY.toFloat()

        windowLayoutParams = WindowManager.LayoutParams(
            viewWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
            applyMaxRefreshRateToLayoutParams(this@FloatingLyricsService, this)
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setContent {
                val appLanguageCode by aiPreferences.appLanguageFlow.collectAsState()
                val strings = remember(appLanguageCode) {
                    resolveAppStrings(appLanguageCode)
                }

                CompositionLocalProvider(LocalStrings provides strings) {
                    val trackMetadata by nowPlayingRepository.trackMetadataFlow.collectAsState()
                    val playbackClock by nowPlayingRepository.playbackClockFlow.collectAsState()
                    val lyricsContent by nowPlayingRepository.lyricsContentFlow.collectAsState()
                    val activeIndex by nowPlayingRepository.currentLineIndexFlow.collectAsState()
                    val lyricOffsetMs by nowPlayingRepository.lyricOffsetMsFlow.collectAsState()
                    val isWordByWordEnabled by nowPlayingRepository.isWordByWordEnabledFlow.collectAsState()
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
                        trackMetadata = trackMetadata,
                        playbackClock = playbackClock,
                        lyricsContent = lyricsContent,
                        activeIndex = activeIndex,
                        lyricOffsetMs = lyricOffsetMs,
                        isWordByWordEnabled = isWordByWordEnabled,
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
                                val currentViewWidth = params.width.takeIf { it > 0 } ?: getSafeOverlayWidthPx(capsuleWidthDp)
                                val bounds = getSafeDragBounds(currentViewWidth, viewHeight)

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
                        onDragEnd = { _ ->
                            windowLayoutParams?.let { params ->
                                cancelSnapAnimation()
                                floatingPreferences.setLastPosition(params.x, params.y, isLandscape())
                            }
                        },
                        onExpandChanged = { isExpanded ->
                            handleExpandChanged(isExpanded)
                        },
                        onPureControlsChanged = { isVisible ->
                            handlePureControlsChanged(isVisible)
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
            val currentWidthPx = params.width.takeIf { it > 0 } ?: getSafeOverlayWidthPx(isLandscape = landscape)
            val expandedHeightPx = (270 * density).toInt()
            val bounds = getSafeDragBounds(currentWidthPx, expandedHeightPx, landscape)

            cancelSnapAnimation()
            if (isExpanded) {
                val targetX = params.x.coerceIn(bounds.left, bounds.right)
                val targetY = params.y.coerceIn(bounds.top, bounds.bottom)
                if (targetX != params.x || targetY != params.y) {
                    params.x = targetX
                    params.y = targetY
                    dragCurrentX = targetX.toFloat()
                    dragCurrentY = targetY.toFloat()
                    try {
                        windowManager?.updateViewLayout(composeView, params)
                    } catch (e: Exception) {}
                }
            } else {
                floatingPreferences.setLastPosition(params.x, params.y, landscape)
            }
        }
    }

    private fun handlePureControlsChanged(visible: Boolean) {
        if (!visible) return
        windowLayoutParams?.let { params ->
            val landscape = isLandscape()
            val density = resources.displayMetrics.density
            val currentWidthPx = params.width.takeIf { it > 0 } ?: getSafeOverlayWidthPx(isLandscape = landscape)
            val baseHeight = composeView?.height?.takeIf { it > 0 } ?: (52 * density).toInt()
            val projectedHeight = maxOf(
                baseHeight + (PURE_CONTROLS_EXTRA_DP * density).toInt(),
                (116 * density).toInt()
            )
            val bounds = getSafeDragBounds(currentWidthPx, projectedHeight, landscape)
            val targetY = params.y.coerceIn(bounds.top, bounds.bottom)
            if (targetY != params.y) {
                params.y = targetY
                dragCurrentY = targetY.toFloat()
                try {
                    windowManager?.updateViewLayout(composeView, params)
                } catch (e: Exception) {}
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        cancelSnapAnimation()
        val landscape = isLandscape()
        val screenBounds = getScreenBounds(landscape)
        val density = resources.displayMetrics.density
        val viewWidth = params.width.takeIf { it > 0 } ?: getSafeOverlayWidthPx(isLandscape = landscape)
        val viewHeight = composeView?.height?.takeIf { it > 0 } ?: (48 * density).toInt()
        val bounds = getSafeDragBounds(viewWidth, viewHeight, landscape)
        val currentX = params.x
        val targetX = if (currentX + (viewWidth / 2) < screenBounds.width() / 2) {
            bounds.left
        } else {
            bounds.right
        }

        snapAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
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

    private fun refreshNotification(locked: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(locked))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val strings = resolveAppStrings(aiPreferences.appLanguage)
            val channel = NotificationChannel(
                CHANNEL_ID,
                strings.floatingChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = strings.floatingChannelDesc
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isLocked: Boolean): Notification {
        val strings = resolveAppStrings(aiPreferences.appLanguage)
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

        val lockActionTitle = if (isLocked) strings.floatingNotifUnlock else strings.floatingNotifLock

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(strings.floatingNotifTitle)
            .setContentText(if (isLocked) strings.floatingNotifTextLocked else strings.floatingNotifTextUnlocked)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, lockActionTitle, toggleLockIntent)
            .addAction(0, strings.floatingNotifClose, closeIntent)
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
            val currentViewWidth = getSafeOverlayWidthPx(floatingPreferences.capsuleWidthDp, isLandscape)
            val currentViewHeight = composeView?.height?.takeIf { it > 0 } ?: (48 * density).toInt()
            val bounds = getSafeDragBounds(currentViewWidth, currentViewHeight, isLandscape)
            val screenBounds = getScreenBounds(isLandscape)
            val insets = getSystemBarInsets()

            val savedX = floatingPreferences.getLastPositionX(isLandscape)
            val savedY = floatingPreferences.getLastPositionY(isLandscape)

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

            params.width = currentViewWidth
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
        setAppInForeground(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunningFlow.value = false
        floatingPreferences.isEnabled = false
        FloatingLyricsTileService.requestSync(this)
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
        const val PURE_CONTROLS_EXTRA_DP = 64

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
