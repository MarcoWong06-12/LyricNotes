package com.linernotes.app.presentation.common

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 锁定当前 Composable 所在视窗（Window）的最高可用显示模式（例如 120Hz 高刷）。
 * 支持 Activity 宿主窗口以及 ModalBottomSheet / Dialog 弹窗独立 Window。
 *
 * 全程采用严格防御性安全调用（catch Throwable），避免在特定 OEM 系统（如小米、OPPO、一加等）
 * 上因缺失字段或非 Visual Context 抛出 NoSuchFieldError / UnsupportedOperationException 导致闪退。
 */
@Composable
fun LockWindowRefreshRateEffect() {
    val view = LocalView.current
    SideEffect {
        try {
            val window = findWindow(view) ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = window.windowManager?.defaultDisplay
                val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            }
        } catch (_: Throwable) {
            // 捕获所有 Throwable 包括 Error / NoSuchFieldError / UnsupportedOperationException，确保绝对不发生闪退
        }
    }
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
    } catch (_: Throwable) {}
    return null
}
