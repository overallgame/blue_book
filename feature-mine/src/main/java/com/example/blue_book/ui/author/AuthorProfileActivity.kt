package com.example.blue_book.ui.author

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import com.example.blue_book.feature_mine.R
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

/**
 * 作者主页：以 mine_page 为模板，无编辑资料/扫一扫，下半部分仅展示该作者作品。
 *
 * 标准启动模式（清单未声明 launchMode）：每次打开都是新实例，叠在当前页面之上，
 * 返回即回到来源。因此从播放页点头像看另一位作者会压出第二个实例、返回时逐层退回，
 * 这正是「从哪来回哪去」的期望；原先按 singleTask 写的 onNewIntent 复用逻辑
 * 在标准模式下永远不会被调用，已删除。
 */
@Route(path = RoutePath.USER_PROFILE)
@AndroidEntryPoint
class AuthorProfileActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_author_profile)
		setupEdgeToEdge()
		if (savedInstanceState == null) {
			showProfile(intent.getLongExtra(ExtraKeys.EXTRA_USER_ID, 0L))
		}
	}

	private fun showProfile(userId: Long) {
		supportFragmentManager.commit {
			replace(R.id.author_profile_container, AuthorProfileFragment().apply {
				arguments = Bundle().apply { putLong(ExtraKeys.EXTRA_USER_ID, userId) }
			})
		}
	}

	/**
	 * 背景图延展到状态栏后方。
	 *
	 * 状态栏图标明暗**交给主题**（AppTheme 已按昼夜配置 windowLightStatusBar），
	 * 这里不再硬编码——原先固定「深色图标」会让深色主题下图标压在深色背景上看不清。
	 */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
	}
}
