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
        val manager = PlaybackStateManager.get(context)
        val current = manager.playbackState.value

        when (action) {
            ACTION_METADATA_CHANGED -> {
                val trackId = intent.getStringExtra("id")
                val artist = intent.getStringExtra("artist") ?: ""
                val album = intent.getStringExtra("album") ?: ""
                val track = intent.getStringExtra("track") ?: ""
                val isSameTrack = (track == current.title && artist == current.artist)
                val length = intent.getIntExtra("length", -1).takeIf { it > 0 }?.toLong()
                    ?: intent.getLongExtra("length", -1L).takeIf { it > 0L }
                    ?: if (isSameTrack) current.durationMs else 0L

                if (track.isNotBlank()) {
                    manager.updateState(
                        current.copy(
                            title = track,
                            artist = artist,
                            album = album,
                            durationMs = length,
                            currentPositionMs = if (isSameTrack) current.currentPositionMs else 0L,
                            coverUrl = if (isSameTrack) current.coverUrl else null,
                            trackId = trackId ?: if (isSameTrack) current.trackId else null,
                            packageName = "com.spotify.music",
                            sourceApp = MediaSourceApp.SPOTIFY,
                            lastUpdateTimeMs = if (isSameTrack) current.lastUpdateTimeMs else SystemClock.elapsedRealtime()
                        )
                    )
                }
            }
            ACTION_PLAYBACK_STATE_CHANGED -> {
                val playing = intent.getBooleanExtra("playing", false)
                val position = intent.getIntExtra("playbackPosition", -1).takeIf { it >= 0 }?.toLong()
                    ?: intent.getLongExtra("playbackPosition", -1L).takeIf { it >= 0L }
                    ?: current.currentPositionMs

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
