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
 * 锁定当前 Composable 所在视窗（Window）的显示刷新率为 120Hz（或硬件最高刷新率）。
 * 支持 Activity 宿主窗口以及 ModalBottomSheet / Dialog 弹窗独立 Window。
 *
 * 在 Android 11+ (API 30+) 上，系统 SurfaceFlinger / DisplayManager 会将独立的 Dialog 视窗
 * 默认限频或动态降速至 60Hz，除非该 Window 主动声明 preferredMinDisplayRefreshRate = 120f。
 * 本组件向上回溯找到 DialogWindowProvider / Activity 并施加 120Hz 锁定，
 * 彻底消除 120Hz 高刷屏（小米、一加、OPPO、三星、LTPO）在 BottomSheet 滚动时的掉帧卡顿。
 */
@Composable
fun LockWindowRefreshRateEffect() {
    val view = LocalView.current
    SideEffect {
        try {
            val window = findWindow(view) ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    preferredMinDisplayRefreshRate = 120f
                    preferredMaxDisplayRefreshRate = 120f
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val maxMode = window.windowManager?.defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            }
        } catch (_: Exception) {
            // 忽略非致命视窗配置异常
        }
    }
}

private fun findWindow(view: View): Window? {
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
    return null
}
