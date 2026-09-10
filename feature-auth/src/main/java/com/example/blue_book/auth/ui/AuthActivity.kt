package com.example.blue_book.auth.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.auth.ui.entry.AuthEntryFragment
import com.example.blue_book.auth.ui.login.LoginFragment
import com.example.blue_book.auth.ui.register.RegisterFragment
import com.example.blue_book.feature_auth.R
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.AUTH)
@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_auth)
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.auth_container, AuthEntryFragment())
			}
		}
	}

	fun navigateToLogin() {
		supportFragmentManager.commit {
			replace(R.id.auth_container, LoginFragment())
			addToBackStack("login")
		}
	}

	fun navigateToRegister() {
		supportFragmentManager.commit {
			replace(R.id.auth_container, RegisterFragment())
			addToBackStack("register")
		}
	}

	/**
	 * 登录/注册完成：返回调用页面（保留任务栈，用户回到原页面继续操作，如播放页点赞）。
	 * 若登录页本身就是任务根（退出登录后进入），则回到首页。
	 */
	fun finishAuth() {
		if (isTaskRoot) {
			TheRouter.build(RoutePath.MAIN)
				.withFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
				.navigation(this)
		}
		finish()
	}
}
