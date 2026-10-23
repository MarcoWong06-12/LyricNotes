package com.linernotes.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.resolveAppStrings
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.presentation.nowplaying.NowPlayingScreen
import com.linernotes.app.presentation.theme.LinerNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var aiPreferences: AiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()

        // 锁定全局 120Hz / 屏幕最高硬件刷新率，彻底消除抽屉与复杂列表的微卡顿
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display ?: window.windowManager?.defaultDisplay
                } else {
                    window.windowManager?.defaultDisplay
                }
                val maxMode = disp?.supportedModes?.maxByOrNull { it.refreshRate }
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = maxMode?.refreshRate?.takeIf { it > 0f } ?: 120f
                    if (maxMode != null) {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        setContent {
            val appLanguageCode by aiPreferences.appLanguageFlow.collectAsState()
            val themeModeCode by aiPreferences.themeModeFlow.collectAsState()
            val isAmoledMode by aiPreferences.isAmoledModeFlow.collectAsState()
            val themeMode = remember(themeModeCode) {
                AiPreferences.ThemeMode.fromCode(themeModeCode)
            }
            val strings = remember(appLanguageCode) {
                resolveAppStrings(appLanguageCode)
            }

            CompositionLocalProvider(LocalStrings provides strings) {
                LinerNotesTheme(themeMode = themeMode, isAmoledMode = isAmoledMode) {
                    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
                    BackHandler {
                        // 按系统返回键退回桌面，保持音乐与歌词伴侣后台运行
                        activity?.moveTaskToBack(true)
                    }

                    NowPlayingScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.linernotes.app.core.floating.FloatingLyricsService.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        com.linernotes.app.core.floating.FloatingLyricsService.setAppInForeground(false)
    }
}
