package com.example.blue_book.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_home.R
import com.example.blue_book.host.IMainHost
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.search.SearchFragment
import com.example.blue_book.ui.search.AfterSearchFragment
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.HOME)
@AndroidEntryPoint
class HomeActivity : AppCompatActivity(), IMainHost {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_home)
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.home_container, HomeFragment())
			}
		}
	}

	override fun navigateToSearch() {
		supportFragmentManager.commit {
			replace(R.id.home_container, SearchFragment())
			addToBackStack("search")
		}
	}

	override fun navigateToSearchResult(keyword: String) {
		supportFragmentManager.commit {
			replace(R.id.home_container, AfterSearchFragment().apply {
				arguments = Bundle().apply { putString(ExtraKeys.EXTRA_KEYWORD, keyword) }
			})
			addToBackStack("search_result")
		}
	}

	override fun navigateToVideoPlayer(
		item: VideoCardInfo,
		source: String?,
		keyword: String?,
		userId: Long
	) {
		TheRouter.build(RoutePath.VIDEO)
			.withParcelable(ExtraKeys.EXTRA_VIDEO, item)
			.apply {
				source?.let { withString(ExtraKeys.EXTRA_SOURCE, it) }
				keyword?.let { withString(ExtraKeys.EXTRA_KEYWORD, it) }
			}
			.navigation(this)
	}

	override fun navigateToAuthEntry() {
		// 不清空任务栈也不 finish：本页面是 Tab 宿主之一，清栈会把整个 App 关掉。
		// 登录成功由 AuthActivity.finishAuth() 自己回到 MAIN。
		TheRouter.build(RoutePath.AUTH).navigation(this)
	}
}
