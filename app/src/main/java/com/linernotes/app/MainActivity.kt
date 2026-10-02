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
        setContent {
            val appLanguageCode by aiPreferences.appLanguageFlow.collectAsState()
            val themeModeCode by aiPreferences.themeModeFlow.collectAsState()
            val themeMode = remember(themeModeCode) {
                AiPreferences.ThemeMode.fromCode(themeModeCode)
            }
            val strings = remember(appLanguageCode) {
                resolveAppStrings(appLanguageCode)
            }

            CompositionLocalProvider(LocalStrings provides strings) {
                LinerNotesTheme(themeMode = themeMode) {
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
}
