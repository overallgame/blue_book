package com.example.blue_book.ui.followlist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_mine.R
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

/** 关注/粉丝列表页：mine 的"关注/粉丝"入口，点击用户进入作者主页 */
@Route(path = RoutePath.FOLLOW_LIST)
@AndroidEntryPoint
class FollowListActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_follow_list)
		if (savedInstanceState == null) {
			val followType = intent.getStringExtra(ExtraKeys.EXTRA_FOLLOW_TYPE) ?: "following"
			val userId = intent.getLongExtra(ExtraKeys.EXTRA_USER_ID, 0L)
			supportFragmentManager.commit {
				replace(R.id.follow_list_container, FollowListFragment().apply {
					arguments = Bundle().apply {
						putString(ExtraKeys.EXTRA_FOLLOW_TYPE, followType)
						putLong(ExtraKeys.EXTRA_USER_ID, userId)
					}
				})
			}
		}
	}
}
