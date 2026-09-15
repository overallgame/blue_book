package com.example.blue_book.ui.mine

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_mine.R
import com.example.blue_book.host.IMainHost
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.profile.ProfileFieldEditFragment
import com.example.blue_book.ui.profile.UserProfileEditFragment
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.MINE)
@AndroidEntryPoint
class MineActivity : AppCompatActivity(), IMainHost {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_mine)
		setupEdgeToEdge()
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.mine_container, MineFragment())
			}
		}
	}

	/** 背景图延展到状态栏后方；状态栏图标使用白天模式（深色） */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
		WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
	}

	override fun navigateToProfileEdit() {
		supportFragmentManager.commit {
			replace(R.id.mine_container, UserProfileEditFragment())
			addToBackStack("profile_edit")
		}
	}

	/** 跳转单字段编辑页（名字/简介/性别/生日/地区/职业/学校） */
	override fun navigateToProfileFieldEdit(field: String) {
		supportFragmentManager.commit {
			replace(R.id.mine_container, ProfileFieldEditFragment().apply {
				arguments = Bundle().apply { putString(ProfileFieldEditFragment.ARG_FIELD, field) }
			})
			addToBackStack("profile_field_edit")
		}
	}

	override fun navigateToAuthEntry() {
		// 不清空任务栈也不 finish：本页面是 Tab 宿主之一，清栈会把整个 App 关掉。
		// 登录成功由 AuthActivity.finishAuth() 自己回到 MAIN。
		TheRouter.build(RoutePath.AUTH).navigation(this)
	}

	/**
	 * 进入播放页并按来源列表续播（首条为点击的视频，后续由播放页按来源游标续拉同源内容）
	 * @param source 列表来源标记（liked/collected/user_videos）
	 * @param userId 作品列表所属用户 id（仅 user_videos）
	 */
	override fun navigateToVideoPlayer(
		item: VideoCardInfo,
		source: String?,
		keyword: String?,
		userId: Long
	) {
		TheRouter.build(RoutePath.VIDEO)
			.withParcelable(ExtraKeys.EXTRA_VIDEO, item)
			.apply { source?.let { withString(ExtraKeys.EXTRA_SOURCE, it) } }
			.withLong(ExtraKeys.EXTRA_SOURCE_USER_ID, userId)
			.navigation(this)
	}
}
