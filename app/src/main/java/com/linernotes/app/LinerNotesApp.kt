package com.linernotes.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.linernotes.app.core.floating.FloatingLyricsService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltAndroidApp
class LinerNotesApp : Application() {

    private var startedActivityCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundRunnable = Runnable {
        if (startedActivityCount == 0) {
            _isForegroundFlow.value = false
            FloatingLyricsService.setAppInForeground(false)
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                mainHandler.removeCallbacks(backgroundRunnable)
                startedActivityCount++
                if (startedActivityCount > 0) {
                    _isForegroundFlow.value = true
                    FloatingLyricsService.setAppInForeground(true)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                    mainHandler.removeCallbacks(backgroundRunnable)
                    mainHandler.postDelayed(backgroundRunnable, FOREGROUND_DEBOUNCE_MS)
                } else if (startedActivityCount == 0) {
                    // 配置變更重建期間保留短暫防抖兜底，若意外未重建仍能歸位
                    mainHandler.removeCallbacks(backgroundRunnable)
                    mainHandler.postDelayed(backgroundRunnable, FOREGROUND_DEBOUNCE_MS * 2)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isFinishing && startedActivityCount == 0 && !activity.isChangingConfigurations) {
                    mainHandler.removeCallbacks(backgroundRunnable)
                    mainHandler.postDelayed(backgroundRunnable, FOREGROUND_DEBOUNCE_MS)
                }
            }
        })
    }

    companion object {
        private const val FOREGROUND_DEBOUNCE_MS = 160L

        private val _isForegroundFlow = MutableStateFlow(false)
        val isForegroundFlow: StateFlow<Boolean> = _isForegroundFlow.asStateFlow()
    }
}
