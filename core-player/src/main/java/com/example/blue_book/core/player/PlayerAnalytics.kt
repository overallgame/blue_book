package com.example.blue_book.core.player

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * 播放器埋点上报接口，默认实现为 Log 输出，可替换为 Firebase/自建埋点等。
 */
interface AnalyticsReporter {
	fun report(event: String, params: Map<String, Any>)

	companion object {
		/** 默认 Log 实现，生产环境应替换 */
		val DEFAULT: AnalyticsReporter = object : AnalyticsReporter {
			override fun report(event: String, params: Map<String, Any>) {
				Log.d("PlayerAnalytics", "$event: $params")
			}
		}
	}
}

class BasicPlayerAnalytics(
	private val reporter: AnalyticsReporter = AnalyticsReporter.DEFAULT
) : Player.Listener {
	private var startMs: Long = 0

	override fun onPlaybackStateChanged(playbackState: Int) {
		when (playbackState) {
			Player.STATE_BUFFERING -> if (startMs == 0L) startMs = System.currentTimeMillis()
			Player.STATE_READY -> {
				if (startMs != 0L) {
					val ttfb = System.currentTimeMillis() - startMs
					reporter.report("player_first_ready", mapOf("ttfb_ms" to ttfb))
					startMs = 0
				}
			}
		}
	}

	override fun onPlayerError(error: PlaybackException) {
		reporter.report("player_error", mapOf("code" to error.errorCode, "msg" to (error.message ?: "")))
	}
}
