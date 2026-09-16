package com.example.blue_book.ui.video

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_video.R
import com.example.blue_book.host.IMainHost
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

/**
 * 播放页（独立页面）：从其它页面点某个视频时压在上层，返回即回到点击它的那个页面。
 *
 * 与底部导航的「视频」Tab 的分工：
 * - 视频 Tab 在 [com.example.blue_book.view.MainActivity] 内，是沉浸式播放 Feed，
 *   属于 Tab 图的一部分，返回回到首页 Tab；
 * - 本页面是独立 Activity，压在当前页面之上，返回回到来源（搜索结果/消息/我的列表/作者主页）。
 *
 * 内容复用 [VideoFragment]：它本来就是「按 arguments 决定播放哪条、按 source 决定怎么续拉」
 * 的参数驱动页面，挂在 Tab 里还是挂在 Activity 里行为一致。
 *
 * 之所以要独立成页面而不是切到视频 Tab：切 Tab 会把选中项改掉，来源页面（例如搜索结果）
 * 的上下文就没了，返回回不到原处。
 */
@Route(path = RoutePath.VIDEO_PLAYER)
@AndroidEntryPoint
class VideoActivity : AppCompatActivity(), IMainHost {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_video_player)
		setupEdgeToEdge()
		// 旋转/进程重建时 VideoFragment 由 FragmentManager 按已保存的 arguments 恢复
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				add(R.id.video_player_container, VideoFragment().apply { arguments = videoArguments(intent) })
			}
		}
	}

	/** 全屏：横屏 + 收起系统栏。本页面没有底部导航，不必像 Tab 那样额外隐藏导航栏 */
	override fun enterFullscreen() {
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		WindowInsetsControllerCompat(window, window.decorView).apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
	}

	override fun exitFullscreen() {
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		WindowInsetsControllerCompat(window, window.decorView)
			.show(WindowInsetsCompat.Type.systemBars())
	}

	/**
	 * 黑底播放页：系统栏透明 + 浅色图标。
	 *
	 * 图标明暗不交给主题：本页面恒为黑底，浅色主题下也必须用浅色图标，
	 * 否则深色图标压在黑底上看不清（与「视频」Tab 同一个道理）。
	 */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
		window.navigationBarColor = Color.TRANSPARENT
		WindowInsetsControllerCompat(window, window.decorView).apply {
			isAppearanceLightStatusBars = false
			isAppearanceLightNavigationBars = false
		}
	}

	/**
	 * 读取路由参数。缺少视频本体属于编程错误（入口只应是 [com.example.blue_book.router.openVideoPlayer]），
	 * 这里让它响亮地失败，而不是打开一个空播放页。
	 */
	private fun videoArguments(source: Intent): Bundle {
		val item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			source.getParcelableExtra(ExtraKeys.EXTRA_VIDEO, VideoCardInfo::class.java)
		} else {
			@Suppress("DEPRECATION")
			source.getParcelableExtra(ExtraKeys.EXTRA_VIDEO)
		}
		checkNotNull(item) { "VideoActivity 缺少 ${ExtraKeys.EXTRA_VIDEO} 参数" }
		return Bundle().apply {
			putParcelable(ExtraKeys.EXTRA_VIDEO, item)
			source.getStringExtra(ExtraKeys.EXTRA_SOURCE)?.let { putString(ExtraKeys.EXTRA_SOURCE, it) }
			source.getStringExtra(ExtraKeys.EXTRA_KEYWORD)?.let { putString(ExtraKeys.EXTRA_KEYWORD, it) }
			putLong(
				ExtraKeys.EXTRA_SOURCE_USER_ID,
				source.getLongExtra(ExtraKeys.EXTRA_SOURCE_USER_ID, 0L)
			)
		}
	}
}
