package com.example.blue_book.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.feature_home.R
import com.example.blue_book.ui.search.SearchFragment
import com.example.blue_book.ui.search.AfterSearchFragment
import dagger.hilt.android.AndroidEntryPoint

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
        val intent = Intent().apply {
            setClassName(packageName, "com.example.blue_book.presentation.video.VideoActivity")
            putExtra("EXTRA_VIDEO", item)
            tag?.let { putExtra("TAG_SHOW", it) }
            keyword?.let { putExtra("keyword", it) }
        }
        startActivity(intent)
    }

    fun navigateToAuthEntry() {
        val intent = Intent().apply {
            setClassName(packageName, "com.example.blue_book.auth.ui.AuthActivity")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
