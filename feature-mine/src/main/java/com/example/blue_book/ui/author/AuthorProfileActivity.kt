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

/** 作者主页：以 mine_page 为模板，无编辑资料/扫一扫，下半部分仅展示该作者作品 */
@Route(path = RoutePath.USER_PROFILE)
@AndroidEntryPoint
class AuthorProfileActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_author_profile)
		setupEdgeToEdge()
		if (savedInstanceState == null) {
			val userId = intent.getLongExtra(ExtraKeys.EXTRA_USER_ID, 0L)
			supportFragmentManager.commit {
				replace(R.id.author_profile_container, AuthorProfileFragment().apply {
					arguments = Bundle().apply { putLong(ExtraKeys.EXTRA_USER_ID, userId) }
				})
			}
		}
	}

	/** 背景图延展到状态栏后方；状态栏图标使用白天模式（深色） */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
		WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
	}
}
