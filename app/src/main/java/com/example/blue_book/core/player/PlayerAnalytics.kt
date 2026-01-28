package com.example.blue_book.core.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.PlaybackStatsListener

class BasicPlayerAnalytics(
    private val onEvent: (String, Map<String, Any>) -> Unit
) : Player.Listener {
    private var startMs: Long = 0

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> if (startMs == 0L) startMs = System.currentTimeMillis()
            Player.STATE_READY -> {
                if (startMs != 0L) {
                    val ttfb = System.currentTimeMillis() - startMs
                    onEvent("player_first_ready", mapOf("ttfb_ms" to ttfb))
                    startMs = 0
                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        onEvent("player_error", mapOf("code" to error.errorCode, "msg" to (error.message ?: "")))
    }
}

fun defaultAnalyticsLogger(): (String, Map<String, Any>) -> Unit = { name, params ->
    // 占位：可替换为你的埋点/日志
    android.util.Log.d("PlayerAnalytics", "$name: $params")
}


