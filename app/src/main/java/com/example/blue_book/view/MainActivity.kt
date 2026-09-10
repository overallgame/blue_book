package com.example.blue_book.view

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.R
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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main_bottom_nav)

		val radioGroup = findViewById<RadioGroup>(R.id.main_navRadioGroup)

		if (savedInstanceState == null) {
			radioGroup.check(R.id.tab_home)
			navigateToTab(RoutePath.HOME)
		}

		radioGroup.setOnCheckedChangeListener { _, checkedId ->
			val targetPath = when (checkedId) {
				R.id.tab_home -> RoutePath.HOME
				R.id.tab_video -> RoutePath.VIDEO
				R.id.tab_message -> RoutePath.MESSAGE
				R.id.tab_mine -> RoutePath.MINE
				else -> return@setOnCheckedChangeListener
			}
			navigateToTab(targetPath)
		}

		// 底部导航正中发布入口（小红书风格）：不切换 tab，直接打开发布页
		findViewById<ImageView>(R.id.publish_btn).setOnClickListener {
			TheRouter.build(RoutePath.PUBLISH).navigation(this)
		}

		messageTab = findViewById(R.id.tab_message)
		refreshUnreadBadge()
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
