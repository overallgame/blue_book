package com.example.blue_book.ui.profile

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.commit
import com.example.blue_book.feature_mine.R
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

/**
 * 资料编辑页（独立页面）：资料页 + 单字段编辑页是本页面内的 Fragment 前后栈，
 * 返回先回到资料页，再返回即回到「我的」Tab。
 *
 * 独立成 Activity 的另一个好处：从图片选择器选完头像回来时，下面的资料页实例还在
 * （Activity 内的 Fragment 栈不会销毁它），已填内容不丢。
 */
@Route(path = RoutePath.PROFILE_EDIT)
@AndroidEntryPoint
class ProfileEditActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_profile_edit)
		setupEdgeToEdge()
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.profile_edit_container, UserProfileEditFragment())
			}
		}
	}

	/**
	 * 资料页 → 单字段编辑页（名字/简介/性别/生日/地区/职业/学校）。
	 *
	 * 两道防护：
	 * - 状态已保存（用户在点击瞬间按了 Home/锁屏）时放弃这次提交：此时提交会抛
	 *   `Can not perform this action after onSaveInstanceState`，而用户并不在看这个页面。
	 * - 已经有字段页时不再压第二层：连点两下不同字段会把两层叠起来，
	 *   返回先回到上一个字段页，很像 bug。
	 */
	fun navigateToFieldEdit(field: String) {
		val fm = supportFragmentManager
		if (fm.isDestroyed || fm.isStateSaved) return
		if (fm.backStackEntryCount > 0) return
		fm.commit {
			setReorderingAllowed(true)
			replace(R.id.profile_edit_container, ProfileFieldEditFragment().apply {
				arguments = Bundle().apply { putString(ProfileFieldEditFragment.ARG_FIELD, field) }
			})
			addToBackStack("profile_field")
		}
	}

	/** 页面底色跟随主题，系统栏图标交给主题；只把内容延展到系统栏后方（避免状态栏色带） */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = Color.TRANSPARENT
	}
}
