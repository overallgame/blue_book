package com.example.blue_book.core.player

import androidx.media3.ui.PlayerView

interface PlayerEngine {
    fun bindTo(playerView: PlayerView)
    fun prepare(url: String)
    fun play()
    fun pause()
    fun release()
    fun setSurfaceProvider(provider: VideoSurfaceProvider?) {}

    // 扩展的核心控制接口（用于项目内核能力）
    fun setPlayWhenReady(ready: Boolean) {}
    fun setVolume(volume: Float) {}
    fun setSpeed(speed: Float) {}
    fun setRepeatMode(mode: Int) {}
    fun seekTo(positionMs: Long) {}
    fun currentPosition(): Long = 0L
    fun duration(): Long = 0L

    fun addListener(listener: PlayerEvents) {}
    fun removeListener(listener: PlayerEvents) {}
}

interface PlayerEvents {
    fun onReady() {}
    fun onBuffering() {}
    fun onEnded() {}
    fun onError(message: String) {}
}


