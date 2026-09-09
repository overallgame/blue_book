package com.example.blue_book.ui.video

import android.graphics.Color
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
			val fragment = VideoFragment().apply {
				// 从首页带视频进入时携带参数定位首个视频；否则走随机视频流
				if (intent.hasExtra(ExtraKeys.EXTRA_VIDEO)) arguments = intent.extras
			}
			supportFragmentManager.commit {
				replace(R.id.video_container, fragment)
			}
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

	/** 进入发布页（分块上传 + 元数据提交） */
	fun navigateToPublish() {
		supportFragmentManager.commit {
			replace(R.id.video_container, com.example.blue_book.ui.publish.PublishFragment())
			addToBackStack("publish")
		}
	}
}
