package com.example.blue_book.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(), true
            )
        }

    @Volatile private var isReleased = false

    // READY 超时与退避重试（仅起播阶段）
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readyTimeoutMs: Long = 5000
    private var backoffBaseMs: Long = 1500
    private var maxTimeoutRetries: Int = 2
    private var timeoutRetryCount: Int = 0
    private var currentUrl: String? = null
    private var timeoutRunnable: Runnable? = null
    private var isInitialBuffering = false
    private var stallRunnable: Runnable? = null
    private var bufferingStartMs: Long = 0L
    private val midStreamStallTimeoutMs = 15_000L

    override fun bindTo(playerView: PlayerView) {
        if (isReleased) return
        if (surfaceProvider == null) {
            playerView.player = player
        } else {
            surfaceProvider!!.getSurface { surface ->
                player.setVideoSurface(surface)
            }
        }
    }

    override fun prepare(url: String) {
        if (isReleased) return
        currentUrl = url
        timeoutRetryCount = 0
        isInitialBuffering = true
        cancelReadyTimeout()
        cancelStallTimeout()
        resetStallRetry()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    override fun play() { if (!isReleased) player.play() }
    override fun pause() { if (!isReleased) player.pause() }
    override fun release() {
        if (isReleased) return
        isReleased = true
        player.clearVideoSurface()
        surfaceProvider?.release()
        surfaceProvider = null
        cancelReadyTimeout()
        cancelStallTimeout()
        player.removeListener(bridgeListener)
        eventBridges.clear()
        player.release()
    }

    override fun setSurfaceProvider(provider: VideoSurfaceProvider?) {
        if (isReleased) return
        surfaceProvider = provider
        if (provider == null) {
            player.clearVideoSurface()
        } else {
            provider.getSurface { surface -> player.setVideoSurface(surface) }
        }
    }

    override fun setPlayWhenReady(ready: Boolean) { if (!isReleased) player.playWhenReady = ready }
    override fun setVolume(volume: Float) { if (!isReleased) player.volume = volume }
    override fun setSpeed(speed: Float) { if (!isReleased) player.setPlaybackSpeed(speed) }
    override fun setRepeatMode(mode: Int) { if (!isReleased) player.repeatMode = mode }
    override fun seekTo(positionMs: Long) { if (!isReleased) player.seekTo(positionMs) }
    override fun currentPosition(): Long = if (isReleased) 0L else player.currentPosition
    override fun duration(): Long = if (isReleased) 0L else player.duration

    private val eventBridges = mutableSetOf<PlayerEvents>()
    private val bridgeListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    eventBridges.forEach { it.onBuffering() }
                    if (isInitialBuffering) {
                        scheduleReadyTimeout()
                    } else {
                        scheduleStallTimeout()
                    }
                }
                Player.STATE_READY -> {
                    isInitialBuffering = false
                    cancelReadyTimeout()
                    cancelStallTimeout()
                    resetStallRetry()
                    eventBridges.forEach { it.onReady() }
                }
                Player.STATE_ENDED -> eventBridges.forEach { it.onEnded() }
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            cancelReadyTimeout()
            cancelStallTimeout()
            eventBridges.forEach { it.onError(error.message ?: "", error.errorCode) }
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
            if (isReleased) return@Runnable
            if (player.playbackState != Player.STATE_READY && currentUrl == url) {
                if (timeoutRetryCount < maxTimeoutRetries) {
                    timeoutRetryCount += 1
                    val pos = player.currentPosition
                    player.stop()
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    if (pos > 0) player.seekTo(pos)
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

    private var stallRetryCount = 0
    private var stallSavedPosition: Long = 0L

    private fun scheduleStallTimeout() {
        cancelStallTimeout()
        bufferingStartMs = SystemClock.elapsedRealtime()
        stallRunnable = Runnable {
            if (!isReleased && player.playbackState == Player.STATE_BUFFERING) {
                if (stallRetryCount < 1) {
                    stallRetryCount++
                    stallSavedPosition = player.currentPosition
                    val url = currentUrl ?: return@Runnable
                    player.stop()
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    if (stallSavedPosition > 0) player.seekTo(stallSavedPosition)
                } else {
                    eventBridges.forEach { it.onError("播放卡顿超时") }
                }
            }
        }
        mainHandler.postDelayed(stallRunnable!!, midStreamStallTimeoutMs)
    }

    private fun resetStallRetry() {
        stallRetryCount = 0
        stallSavedPosition = 0L
    }

    private fun cancelStallTimeout() {
        stallRunnable?.let { mainHandler.removeCallbacks(it) }
        stallRunnable = null
        bufferingStartMs = 0L
    }
}
