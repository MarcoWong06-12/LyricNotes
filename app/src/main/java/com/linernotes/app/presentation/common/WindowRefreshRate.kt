package com.linernotes.app.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 锁定当前 Composable 所在视窗的同分辨率最高可用刷新率。
 * 每个 Window 仅应用一次，避免 Recompose 期间频繁触发 WindowManager Binder IPC；
 * 保留 Throwable 捕获以兼容部分 OEM 非 Visual Context 限制。
 */
@Composable
fun LockWindowRefreshRateEffect() {
    val view = LocalView.current
    val appliedWindowRef = remember { arrayOfNulls<Window>(1) }
    val appliedModeId = remember { intArrayOf(-1) }

    SideEffect {
        try {
            val window = findWindow(view) ?: return@SideEffect
            if (appliedWindowRef[0] === window && appliedModeId[0] != -1) return@SideEffect

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = resolveDisplay(view, window) ?: return@SideEffect
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
                    appliedModeId[0] = maxMode.modeId
                }
                appliedWindowRef[0] = window
            }
        } catch (_: Throwable) {
            // 防止特定 OEM 在窗口切换过渡态抛出 Error 或 UnsupportedOperationException
        }
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
