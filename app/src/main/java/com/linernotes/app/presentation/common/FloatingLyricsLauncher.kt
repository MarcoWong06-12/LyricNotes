package com.linernotes.app.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.linernotes.app.core.floating.FloatingLyricsService

/**
 * Starts the floating lyrics overlay after verifying overlay permission and
 * requesting POST_NOTIFICATIONS on demand on Android 13+.
 */
@Composable
fun rememberFloatingLyricsLauncher(): () -> Unit {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        if (Settings.canDrawOverlays(context)) {
            FloatingLyricsService.start(context)
        }
    }

    return remember(context, notificationPermissionLauncher) {
        {
            if (!Settings.canDrawOverlays(context)) {
                FloatingLyricsService.requestOverlayPermission(context)
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                FloatingLyricsService.start(context)
            }
        }
    }
}
