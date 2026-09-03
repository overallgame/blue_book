package com.example.blue_book.ui.mine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_mine.R
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.profile.ProfileFieldEditFragment
import com.example.blue_book.ui.profile.UserProfileEditFragment
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.MINE)
@AndroidEntryPoint
class MineActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_mine)
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.mine_container, MineFragment())
			}
		}
	}

	fun navigateToProfileEdit() {
		supportFragmentManager.commit {
			replace(R.id.mine_container, UserProfileEditFragment())
			addToBackStack("profile_edit")
		}
	}

	/** 跳转单字段编辑页（名字/简介/性别/生日/地区/职业/学校） */
	fun navigateToProfileFieldEdit(field: String) {
		supportFragmentManager.commit {
			replace(R.id.mine_container, ProfileFieldEditFragment().apply {
				arguments = Bundle().apply { putString(ProfileFieldEditFragment.ARG_FIELD, field) }
			})
			addToBackStack("profile_field_edit")
		}
	}

	fun navigateToAuthEntry() {
		TheRouter.build(RoutePath.AUTH)
			.withFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
			.navigation(this)
		finish()
	}

	fun navigateToVideoPlayer(item: VideoCardInfo) {
		TheRouter.build(RoutePath.VIDEO)
			.withParcelable(ExtraKeys.EXTRA_VIDEO, item)
			.navigation(this)
	}
}
