package com.example.blue_book.ui.video

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import androidx.media3.common.util.UnstableApi
import com.example.blue_book.feature_video.R
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.VIDEO)
@AndroidEntryPoint
class VideoActivity : AppCompatActivity() {
	@OptIn(UnstableApi::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_video)
		setupEdgeToEdge()
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.video_container, buildFragment(intent))
			}
		}
	}

	/**
	 * singleTask 复用时必须处理新 Intent：
	 * 带新视频参数则重建播放页，否则保留当前播放会话（底部导航回到视频 Tab 时无参数）。
	 * 不处理会导致"点了另一个视频却还在播上一个"。
	 */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		if (!intent.hasExtra(ExtraKeys.EXTRA_VIDEO)) return
		supportFragmentManager.commit {
			replace(R.id.video_container, buildFragment(intent))
		}
	}

	/** 从首页带视频进入时携带参数定位首个视频；否则走随机视频流（VideoFragment 带 @UnstableApi，引用需 opt-in） */
	@OptIn(UnstableApi::class)
	private fun buildFragment(intent: Intent): VideoFragment {
		return VideoFragment().apply {
			if (intent.hasExtra(ExtraKeys.EXTRA_VIDEO)) arguments = intent.extras
		}
	}

	/** 播放页纯黑背景延展到系统栏后方；状态栏/导航栏图标使用浅色（白色） */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
		window.navigationBarColor = Color.TRANSPARENT
		val controller = WindowInsetsControllerCompat(window, window.decorView)
		controller.isAppearanceLightStatusBars = false
		controller.isAppearanceLightNavigationBars = false
	}

	/** 全屏（横屏播放）：旋转 + 隐藏系统栏（轻扫可临时唤出） */
	fun enterFullscreen() {
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		WindowInsetsControllerCompat(window, window.decorView).apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
	}

	/** 退出全屏：恢复竖屏 + 显示系统栏 */
	fun exitFullscreen() {
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
	}
}
