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

        when (action) {
            ACTION_METADATA_CHANGED -> {
                val trackId = intent.getStringExtra("id")
                val artist = intent.getStringExtra("artist") ?: ""
                val album = intent.getStringExtra("album") ?: ""
                val track = intent.getStringExtra("track") ?: ""
                val playing = intent.getBooleanExtra("playing", false)
                val length = intent.getIntExtra("length", -1).takeIf { it > 0 }?.toLong()
                    ?: intent.getLongExtra("length", -1L).takeIf { it > 0L }
                    ?: 0L

                if (track.isNotBlank()) {
                    manager.updateMetadataAndCover(
                        title = track,
                        artist = artist,
                        album = album,
                        durationMs = length,
                        coverUrl = null,
                        packageName = SPOTIFY_PACKAGE,
                        trackId = trackId,
                        sourceApp = MediaSourceApp.SPOTIFY,
                        precondition = { current -> shouldAcceptSpotifyBroadcast(current, playing) }
                    )
                }
            }
            ACTION_PLAYBACK_STATE_CHANGED -> {
                val playing = intent.getBooleanExtra("playing", false)
                val position = intent.getIntExtra("playbackPosition", -1).takeIf { it >= 0 }?.toLong()
                    ?: intent.getLongExtra("playbackPosition", -1L).takeIf { it >= 0L }

                manager.updatePlaybackClock(
                    positionMs = position,
                    isPlaying = playing,
                    playbackSpeed = null,
                    lastUpdateTimeMs = SystemClock.elapsedRealtime(),
                    requireValidTrack = true,
                    precondition = { current -> shouldAcceptSpotifyBroadcast(current, playing) }
                )
            }
        }
    }

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"

        internal fun shouldAcceptSpotifyBroadcast(
            current: TrackPlaybackState,
            isPlayingExtra: Boolean
        ): Boolean {
            val isSpotifyActiveSource = current.packageName == SPOTIFY_PACKAGE ||
                current.sourceApp == MediaSourceApp.SPOTIFY ||
                current.title.isBlank()
            if (!isSpotifyActiveSource && (!isPlayingExtra || current.isPlaying)) {
                return false
            }
            return true
        }
    }
}
