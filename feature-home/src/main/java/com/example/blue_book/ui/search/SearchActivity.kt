package com.example.blue_book.ui.search

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.commit
import com.example.blue_book.feature_home.R
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

/**
 * 搜索页（独立页面）：搜索页 + 搜索结果页是本页面内的 Fragment 前后栈，
 * 返回箭头/返回键先回到搜索页，再返回即回到打开搜索的那个页面（首页 Tab）。
 *
 * 之所以独立成 Activity 而不是叠在 Tab 之上：叠层方案里搜索结果页属于 Tab 图，
 * 从结果页点视频会切走 Tab，返回就回不到结果页了；独立成页面后，
 * 「结果页 → 播放页 → 返回结果页」由任务栈天然保证。
 */
@Route(path = RoutePath.SEARCH)
@AndroidEntryPoint
class SearchActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_search)
		setupEdgeToEdge()
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.search_container, SearchFragment())
			}
		}
	}

	/**
	 * 搜索页 → 搜索结果页：本页面内压一层，返回回到搜索页（保留已输入的关键词与联想）。
	 *
	 * 两道防护：
	 * - 状态已保存（用户在跳转瞬间按了 Home/锁屏）时放弃这次提交——此时提交会抛
	 *   `Can not perform this action after onSaveInstanceState`，而用户并不在看这个页面，
	 *   返回时搜索页仍在、关键词也还在输入框里。
	 * - 已经有结果页时不再压第二层：连点两下会把两层结果叠起来，
	 *   返回要按两次才回到搜索页，看起来像 bug。
	 */
	fun navigateToSearchResult(keyword: String) {
		val fm = supportFragmentManager
		if (fm.isDestroyed || fm.isStateSaved) return
		if (fm.backStackEntryCount > 0) return
		fm.commit {
			setReorderingAllowed(true)
			replace(R.id.search_container, AfterSearchFragment().apply {
				arguments = Bundle().apply { putString(ExtraKeys.EXTRA_KEYWORD, keyword) }
			})
			addToBackStack("search_result")
		}
	}

	/**
	 * 页面底色跟随主题，系统栏图标也交给主题（AppTheme 已按昼夜配置 windowLightStatusBar），
	 * 这里只把内容延展到系统栏后方——页面自身的底色因此连续到状态栏，不会出现色带。
	 */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
	}
}
