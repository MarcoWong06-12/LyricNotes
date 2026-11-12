package com.linernotes.app.core.floating

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.linernotes.app.core.i18n.resolveAppStrings
import com.linernotes.app.core.preference.AiPreferencesEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 系统快捷设置磁贴：一键开关桌面悬浮歌词（并同步多语言标签）。
 */
@RequiresApi(Build.VERSION_CODES.N)
class FloatingLyricsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val strings = currentStrings()
        if (FloatingLyricsService.isServiceRunning) {
            FloatingLyricsService.stop(this)
            updateTile(isActive = false, label = strings.floatingTileLabel)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, strings.floatingTilePermissionToast, Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        FloatingLyricsService.start(this)
        updateTile(isActive = true, label = strings.floatingTileLabel)
    }

    private fun syncTileState() {
        val strings = currentStrings()
        updateTile(
            isActive = FloatingLyricsService.isServiceRunning,
            label = strings.floatingTileLabel
        )
    }

    private fun updateTile(isActive: Boolean, label: String) {
        val tile = qsTile ?: return
        tile.label = label
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun currentStrings(): com.linernotes.app.core.i18n.AppStrings {
        val langCode = runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                AiPreferencesEntryPoint::class.java
            ).aiPreferences().appLanguage
        }.getOrDefault(com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code)
        return resolveAppStrings(langCode)
    }

    companion object {
        fun requestSync(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    requestListeningState(
                        context.applicationContext,
                        ComponentName(context.applicationContext, FloatingLyricsTileService::class.java)
                    )
                }
            }
        }
    }
}
