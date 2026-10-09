package com.linernotes.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.linernotes.app.core.preference.AiPreferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = VaultBlack,
    surface = VaultSurface,
    surfaceVariant = VaultSurfaceVariant,
    onPrimary = VaultBlack,
    onSecondary = VaultBlack,
    onBackground = OnSurfaceWhite,
    onSurface = OnSurfaceWhite,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Color(0xFF383840)
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121214),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = OnSurfaceWhite,
    onSurface = OnSurfaceWhite,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Color(0xFF28282D)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryAccent,
    secondary = LightSecondaryAccent,
    background = PaperWhite,
    surface = PaperSurface,
    surfaceVariant = PaperSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline
)

@Composable
fun LinerNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    isAmoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        darkTheme && isAmoledMode -> AmoledDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

