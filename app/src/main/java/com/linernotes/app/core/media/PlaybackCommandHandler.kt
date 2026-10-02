package com.linernotes.app.core.media

interface PlaybackCommandHandler {
    fun onPlay()
    fun onPause()
    fun onTogglePlay()
    fun onNext()
    fun onPrevious()
    fun onSeekTo(posMs: Long)
}
