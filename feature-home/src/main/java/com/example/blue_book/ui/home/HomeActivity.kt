package com.example.blue_book.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_home.R
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.search.SearchFragment
import com.example.blue_book.ui.search.AfterSearchFragment
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.HOME)
@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.home_container, HomeFragment())
            }
        }
    }

    fun navigateToSearch() {
        supportFragmentManager.commit {
            replace(R.id.home_container, SearchFragment())
            addToBackStack("search")
        }
    }

    fun navigateToSearchResult(keyword: String) {
        supportFragmentManager.commit {
            replace(R.id.home_container, AfterSearchFragment().apply {
                arguments = Bundle().apply { putString("keyword", keyword) }
            })
            addToBackStack("search_result")
        }
    }

    fun navigateToVideoPlayer(item: VideoCardInfo, tag: String? = null, keyword: String? = null) {
        TheRouter.build(RoutePath.VIDEO)
            .withParcelable("EXTRA_VIDEO", item)
            .apply {
                tag?.let { withString("TAG_SHOW", it) }
                keyword?.let { withString("keyword", it) }
            }
            .navigation(this)
    }

    fun navigateToAuthEntry() {
        TheRouter.build(RoutePath.AUTH)
            .withFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            .navigation(this)
        finish()
    }
}
