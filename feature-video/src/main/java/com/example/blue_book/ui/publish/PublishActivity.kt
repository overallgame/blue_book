package com.example.blue_book.ui.publish

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_video.R
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

/** 视频发布页独立入口：由主界面底部导航正中按钮进入（小红书风格） */
@Route(path = RoutePath.PUBLISH)
@AndroidEntryPoint
class PublishActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_publish)
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.publish_container, PublishFragment())
			}
		}
	}
}
