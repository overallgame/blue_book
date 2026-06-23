package com.example.blue_book.core.player

import androidx.media3.ui.PlayerView

interface PlayerEngine {
    fun bindTo(playerView: PlayerView)
    fun prepare(url: String)
    fun play()
    fun pause()
    fun release()
    fun setSurfaceProvider(provider: VideoSurfaceProvider?)
    fun setPlayWhenReady(ready: Boolean)
    fun setVolume(volume: Float)
    fun setSpeed(speed: Float)
    fun setRepeatMode(mode: Int)
    fun seekTo(positionMs: Long)
    fun currentPosition(): Long
    fun duration(): Long

    fun addListener(listener: PlayerEvents)
    fun removeListener(listener: PlayerEvents)
}

interface PlayerEvents {
    fun onReady() = Unit
    fun onBuffering() = Unit
    fun onEnded() = Unit
    fun onError(message: String, errorCode: Int = 0) = Unit
}
