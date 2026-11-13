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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = resolveDisplay(view, window) ?: return
            val currentMode = display.mode
            val supportedModes = display.supportedModes
            val maxMode = supportedModes
                .filter { mode ->
                    currentMode == null || (
                        mode.physicalWidth == currentMode.physicalWidth &&
                            mode.physicalHeight == currentMode.physicalHeight
                        )
                }
                .maxByOrNull { it.refreshRate }
                ?: supportedModes.maxByOrNull { it.refreshRate }

            if (maxMode != null) {
                val attrs = window.attributes
                if (attrs.preferredDisplayModeId != maxMode.modeId ||
                    attrs.preferredRefreshRate != maxMode.refreshRate
                ) {
                    attrs.preferredDisplayModeId = maxMode.modeId
                    attrs.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = attrs
                }
            }
        }
    } catch (_: Throwable) {
        // 防止特定 OEM 在窗口切换过渡态抛出 Error 或 UnsupportedOperationException
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
