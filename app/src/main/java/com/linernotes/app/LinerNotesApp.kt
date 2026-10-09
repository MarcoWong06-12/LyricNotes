package com.linernotes.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.linernotes.app.core.floating.FloatingLyricsService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LinerNotesApp : Application() {

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount > 0) {
                    FloatingLyricsService.setAppInForeground(true)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    FloatingLyricsService.setAppInForeground(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isFinishing && startedActivityCount == 0) {
                    FloatingLyricsService.setAppInForeground(false)
                }
            }
        })
    }
}
