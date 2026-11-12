package com.linernotes.app

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linernotes.app.core.i18n.LocalStrings
import com.linernotes.app.core.i18n.resolveAppStrings
import com.linernotes.app.core.playback.PlaybackStateManager
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.presentation.nowplaying.NowPlayingScreen
import com.linernotes.app.presentation.theme.LinerNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var aiPreferences: AiPreferences

    @Inject
    lateinit var playbackStateManager: PlaybackStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val initialThemeMode = AiPreferences.ThemeMode.fromCode(aiPreferences.themeMode)
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialDark = when (initialThemeMode) {
            AiPreferences.ThemeMode.SYSTEM -> isSystemDark
            AiPreferences.ThemeMode.LIGHT -> false
            AiPreferences.ThemeMode.DARK -> true
        }
        val barStyle = if (initialDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )

        playbackStateManager.refreshNotificationAccess(this)

        setContent {
            val appLanguageCode by aiPreferences.appLanguageFlow.collectAsStateWithLifecycle()
            val themeModeCode by aiPreferences.themeModeFlow.collectAsStateWithLifecycle()
            val isAmoledMode by aiPreferences.isAmoledModeFlow.collectAsStateWithLifecycle()
            val themeMode = remember(themeModeCode) {
                AiPreferences.ThemeMode.fromCode(themeModeCode)
            }
            val strings = remember(appLanguageCode) {
                resolveAppStrings(appLanguageCode)
            }

            CompositionLocalProvider(LocalStrings provides strings) {
                LinerNotesTheme(themeMode = themeMode, isAmoledMode = isAmoledMode) {
                    val activity = LocalContext.current as? ComponentActivity
                    BackHandler {
                        // 按系統返回鍵退回桌面，保持音樂與歌詞伴侶後台運行
                        activity?.moveTaskToBack(true)
                    }

                    NowPlayingScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playbackStateManager.refreshNotificationAccess(this)
    }
}
