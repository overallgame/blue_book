package com.example.blue_book.view

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.blue_book.R
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.MAIN)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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
	}

	private fun navigateToTab(path: String) {
		TheRouter.build(path)
			.withFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
			.navigation(this)
	}
}
