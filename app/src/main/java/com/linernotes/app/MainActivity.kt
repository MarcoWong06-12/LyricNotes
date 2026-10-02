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
import com.linernotes.app.presentation.booklet.LyricBookletScreen
import com.linernotes.app.presentation.shelf.CdShelfScreen
import com.linernotes.app.presentation.theme.LinerNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import com.linernotes.app.presentation.nowplaying.NowPlayingScreen

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
                    var currentScreen by rememberSaveable { mutableStateOf("NOW_PLAYING") }
                    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }

                    // 全局返回处理：内页返回唱片架，唱片架返回 Now Playing 主页
                    BackHandler(enabled = currentScreen != "NOW_PLAYING") {
                        if (currentScreen == "BOOKLET") {
                            currentScreen = "CD_SHELF"
                            selectedAlbumId = null
                        } else if (currentScreen == "CD_SHELF") {
                            currentScreen = "NOW_PLAYING"
                        }
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == "NOW_PLAYING") {
                                (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(220)))
                                    .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(180)))
                            } else {
                                (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(220)))
                                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(180)))
                            }
                        },
                        label = "mainScreenNavigationAnimation"
                    ) { screen ->
                        when (screen) {
                            "NOW_PLAYING" -> {
                                NowPlayingScreen(
                                    onNavigateToShelf = { currentScreen = "CD_SHELF" }
                                )
                            }
                            "CD_SHELF" -> {
                                CdShelfScreen(
                                    onNavigateToBooklet = { albumId ->
                                        selectedAlbumId = albumId
                                        currentScreen = "BOOKLET"
                                    }
                                )
                            }
                            "BOOKLET" -> {
                                LyricBookletScreen(
                                    albumId = selectedAlbumId ?: "",
                                    onNavigateBack = {
                                        currentScreen = "CD_SHELF"
                                        selectedAlbumId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
