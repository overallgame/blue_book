package com.example.blue_book.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.R
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.provider.INotificationProvider
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route(path = RoutePath.MAIN)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

	private lateinit var messageTab: BadgeRadioButton
	private lateinit var radioGroup: RadioGroup

	/** 当前选中的导航项（用于未登录拦截时回退选中） */
	private var currentCheckedId = R.id.tab_home

	/** 登录态：null=判断中（放行，避免误拦已登录用户） */
	private var loggedIn: Boolean? = null

	/** 每次进入主界面只弹一次登录引导 */
	private var guideShown = false

	private val locationPermissionLauncher =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) {
			/* 拒绝不阻塞：未授权时本地流自动回退推荐内容 */
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main_bottom_nav)

		radioGroup = findViewById(R.id.main_navRadioGroup)

		if (savedInstanceState == null) {
			radioGroup.check(R.id.tab_home)
			navigateToTab(RoutePath.HOME)
		}

		radioGroup.setOnCheckedChangeListener { _, checkedId ->
			// 拦截回退与重复选中（防递归导航）
			if (checkedId == currentCheckedId) return@setOnCheckedChangeListener
			val targetPath = when (checkedId) {
				R.id.tab_home -> RoutePath.HOME
				R.id.tab_video -> RoutePath.VIDEO
				R.id.tab_message -> RoutePath.MESSAGE
				R.id.tab_mine -> RoutePath.MINE
				else -> return@setOnCheckedChangeListener
			}
			// 未登录：除首页外均拦截，回退选中并弹出登录引导卡片
			if (loggedIn == false && checkedId != R.id.tab_home) {
				radioGroup.check(currentCheckedId)
				LoginGuideDialog.show(this)
				return@setOnCheckedChangeListener
			}
			currentCheckedId = checkedId
			navigateToTab(targetPath)
		}

		// 底部导航正中发布入口（小红书风格）：未登录先引导，已登录直接打开发布页
		findViewById<ImageView>(R.id.publish_btn).setOnClickListener {
			if (loggedIn == false) {
				LoginGuideDialog.show(this)
			} else {
				TheRouter.build(RoutePath.PUBLISH).navigation(this)
			}
		}

		messageTab = findViewById(R.id.tab_message)
		refreshUnreadBadge()
		resolveLoginState()
	}

	/** 登录态：已登录在首页先行申请定位权限；未登录弹出登录引导卡片 */
	private fun resolveLoginState() {
		lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				TheRouter.get(IAuthProvider::class.java)?.isLoggedIn() ?: false
			}
			loggedIn = logged
			if (logged) {
				requestLocationPermission()
			} else if (!guideShown) {
				guideShown = true
				LoginGuideDialog.show(this@MainActivity)
			}
		}
	}

	/** 首页先行动态申请定位权限（"本地"流依赖，未授权时端上自动降级） */
	private fun requestLocationPermission() {
		val granted = ContextCompat.checkSelfPermission(
			this, Manifest.permission.ACCESS_COARSE_LOCATION
		) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
		}
	}

	override fun onResume() {
		super.onResume()
		// 从消息页返回后同步角标
		refreshUnreadBadge()
	}

	/** 未读消息角标：静默刷新（未登录/失败时清零） */
	private fun refreshUnreadBadge() {
		lifecycleScope.launch {
			val count = withContext(Dispatchers.IO) {
				TheRouter.get(INotificationProvider::class.java)
					?.unreadCount()?.getOrNull() ?: 0L
			}
			messageTab.setUnreadCount(count.toInt())
		}
	}

	private fun navigateToTab(path: String) {
		TheRouter.build(path)
			.withFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
			.navigation(this)
	}
}
