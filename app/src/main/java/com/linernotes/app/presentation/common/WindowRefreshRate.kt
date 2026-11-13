package com.linernotes.app.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 锁定当前 Composable 所在视窗的同分辨率最高可用刷新率。
 * 在首次挂载、窗口获焦及宿主生命周期恢复时校对并锁定高刷模式（已锁定时零 Binder IPC）；
 * 保留 Throwable 捕获以兼容部分 OEM 非 Visual Context 限制。
 */
@Composable
fun LockWindowRefreshRateEffect() {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(view, lifecycleOwner) {
        applyMaxRefreshRate(view)
        val postRunnable = Runnable { applyMaxRefreshRate(view) }
        view.post(postRunnable)

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                applyMaxRefreshRate(view)
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyMaxRefreshRate(view)
            }
        }

        val vto = view.viewTreeObserver
        if (vto.isAlive) {
            vto.addOnWindowFocusChangeListener(focusListener)
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            view.removeCallbacks(postRunnable)
            if (vto.isAlive) {
                vto.removeOnWindowFocusChangeListener(focusListener)
            }
            val currentVto = view.viewTreeObserver
            if (currentVto.isAlive && currentVto !== vto) {
                currentVto.removeOnWindowFocusChangeListener(focusListener)
            }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
}

private fun applyMaxRefreshRate(view: View) {
    try {
        val window = findWindow(view) ?: return
        val display = resolveDisplay(view, window) ?: return
        val attrs = window.attributes
        if (applyMaxRefreshRateToLayoutParams(window.context, attrs, display)) {
            window.attributes = attrs
        }
    } catch (_: Throwable) {
        // 防止特定 OEM 在窗口切换过渡态抛出 Error 或 UnsupportedOperationException
    }
}

/**
 * 将 WindowManager.LayoutParams 锁定至指定（或默认）屏幕同分辨率最高可用刷新率，
 * 供 Activity / Dialog 与系统悬浮窗 WindowManager 复用（已处于目标模式时零变更）。
 */
fun applyMaxRefreshRateToLayoutParams(
    context: Context,
    attrs: WindowManager.LayoutParams,
    display: Display? = null
): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val targetDisplay = display
                ?: (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return false
            val currentMode = targetDisplay.mode
            val supportedModes = targetDisplay.supportedModes
            val maxMode = supportedModes
                .filter { mode ->
                    currentMode == null || (
                        mode.physicalWidth == currentMode.physicalWidth &&
                            mode.physicalHeight == currentMode.physicalHeight
                        )
                }
                .maxByOrNull { it.refreshRate }
                ?: supportedModes.maxByOrNull { it.refreshRate }

            if (maxMode != null &&
                (attrs.preferredDisplayModeId != maxMode.modeId ||
                    attrs.preferredRefreshRate != maxMode.refreshRate)
            ) {
                attrs.preferredDisplayModeId = maxMode.modeId
                attrs.preferredRefreshRate = maxMode.refreshRate
                true
            } else {
                false
            }
        } else {
            false
        }
    } catch (_: Throwable) {
        // 防止特定 OEM 在窗口切换或非 Visual Context 场景抛出 Error / UnsupportedOperationException
        false
    }
}

private fun resolveDisplay(view: View, window: Window): Display? {
    view.display?.let { return it }
    window.decorView.display?.let { return it }
    val dm = view.context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    return dm?.getDisplay(Display.DEFAULT_DISPLAY)
}

private fun findWindow(view: View): Window? {
    try {
        var parent = view.parent
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                return parent.window
            }
            parent = parent.parent
        }
        var ctx = view.context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx.window
            ctx = ctx.baseContext
        }
    } catch (_: Throwable) {
    }
    return null
}
