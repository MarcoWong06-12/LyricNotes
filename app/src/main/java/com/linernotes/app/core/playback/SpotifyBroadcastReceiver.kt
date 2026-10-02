package com.linernotes.app.core.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * 监听 Spotify 官方设备广播 (Device Broadcast Status)
 * 当用户在 Spotify 设置开启“向其他应用广播设备状态”时触发
 */
class SpotifyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val manager = PlaybackStateManager.getInstance()
        val current = manager.playbackState.value

        when (action) {
            ACTION_METADATA_CHANGED -> {
                val trackId = intent.getStringExtra("id") ?: current.trackId
                val artist = intent.getStringExtra("artist") ?: ""
                val album = intent.getStringExtra("album") ?: ""
                val track = intent.getStringExtra("track") ?: ""
                val length = intent.getIntExtra("length", 0).takeIf { it > 0 }?.toLong()
                    ?: intent.getLongExtra("length", current.durationMs)

                if (track.isNotBlank()) {
                    manager.updateState(
                        current.copy(
                            title = track,
                            artist = artist,
                            album = album,
                            durationMs = length,
                            trackId = trackId,
                            packageName = "com.spotify.music",
                            sourceApp = MediaSourceApp.SPOTIFY,
                            lastUpdateTimeMs = SystemClock.elapsedRealtime()
                        )
                    )
                }
            }
            ACTION_PLAYBACK_STATE_CHANGED -> {
                val playing = intent.getBooleanExtra("playing", false)
                val position = intent.getIntExtra("playbackPosition", 0).toLong().takeIf { it >= 0 }
                    ?: intent.getLongExtra("playbackPosition", current.currentPositionMs)

                manager.updatePositionOnly(position, playing)
            }
        }
    }

    companion object {
        const val ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"
    }
}
