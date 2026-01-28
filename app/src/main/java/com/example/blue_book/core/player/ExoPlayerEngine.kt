package com.example.blue_book.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi

@UnstableApi
class ExoPlayerEngine(
    private val context: Context,
    private val factories: PlayerFactories = PlayerFactoriesProvider.default(context),
    private var surfaceProvider: VideoSurfaceProvider? = null
) : PlayerEngine {

    private val player: ExoPlayer = ExoPlayer.Builder(context, factories.renderersFactory)
        .setTrackSelector(factories.trackSelector)
        .setLoadControl(factories.loadControl)
        .setMediaSourceFactory(factories.mediaSourceFactory)
        .build().apply {
            factories.analyticsListener?.let { addListener(it) }
            // 基础音频焦点与属性
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(), true
            )
        }

    // READY 超时与退避重试
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readyTimeoutMs: Long = 5000
    private var backoffBaseMs: Long = 1500
    private var maxTimeoutRetries: Int = 2
    private var timeoutRetryCount: Int = 0
    private var currentUrl: String? = null
    private var timeoutRunnable: Runnable? = null

    override fun bindTo(playerView: PlayerView) {
        if (surfaceProvider == null) {
            playerView.player = player
        } else {
            // 使用自定义 Surface，不绑定到 PlayerView 的内部 SurfaceView
            surfaceProvider!!.getSurface { surface ->
                player.setVideoSurface(surface)
            }
        }
    }

    override fun prepare(url: String) {
        currentUrl = url
        timeoutRetryCount = 0
        cancelReadyTimeout()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    override fun play() { player.play() }
    override fun pause() { player.pause() }
    override fun release() {
        player.clearVideoSurface()
        surfaceProvider?.release()
        cancelReadyTimeout()
        player.release()
    }

    override fun setSurfaceProvider(provider: VideoSurfaceProvider?) {
        surfaceProvider = provider
        if (provider == null) {
            player.clearVideoSurface()
        } else {
            provider.getSurface { surface -> player.setVideoSurface(surface) }
        }
    }

    // 扩展能力实现
    override fun setPlayWhenReady(ready: Boolean) { player.playWhenReady = ready }
    override fun setVolume(volume: Float) { player.volume = volume }
    override fun setSpeed(speed: Float) { player.setPlaybackSpeed(speed) }
    override fun setRepeatMode(mode: Int) { player.repeatMode = mode }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
    override fun currentPosition(): Long = player.currentPosition
    override fun duration(): Long = player.duration

    private val eventBridges = mutableSetOf<PlayerEvents>()
    private val bridgeListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    eventBridges.forEach { it.onBuffering() }
                    scheduleReadyTimeout()
                }
                Player.STATE_READY -> {
                    cancelReadyTimeout()
                    eventBridges.forEach { it.onReady() }
                }
                Player.STATE_ENDED -> eventBridges.forEach { it.onEnded() }
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            cancelReadyTimeout()
            eventBridges.forEach { it.onError(error.message ?: "") }
        }
    }

    init { player.addListener(bridgeListener) }
    override fun addListener(listener: PlayerEvents) { eventBridges.add(listener) }
    override fun removeListener(listener: PlayerEvents) { eventBridges.remove(listener) }

    private fun scheduleReadyTimeout() {
        val url = currentUrl ?: return
        cancelReadyTimeout()
        val delay = readyTimeoutMs + backoffBaseMs * timeoutRetryCount
        timeoutRunnable = Runnable {
            // 若仍未 READY，则执行退避重试或上报错误
            if (player.playbackState != Player.STATE_READY && currentUrl == url) {
                if (timeoutRetryCount < maxTimeoutRetries) {
                    timeoutRetryCount += 1
                    // 记录当前位置以尽量平滑恢复
                    val pos = player.currentPosition
                    player.stop()
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    if (pos > 0) player.seekTo(pos)
                    // 新一轮 BUFFERING 时会重新计时
                } else {
                    eventBridges.forEach { it.onError("起播超时") }
                    cancelReadyTimeout()
                }
            }
        }
        mainHandler.postDelayed(timeoutRunnable!!, delay)
    }

    private fun cancelReadyTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }
}


