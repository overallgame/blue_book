package com.example.blue_book.ui.video

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.media3.common.util.UnstableApi
import com.example.blue_book.feature_video.R
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.VIDEO)
@AndroidEntryPoint
class VideoActivity : AppCompatActivity() {
	@OptIn(UnstableApi::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_video)
		if (savedInstanceState == null) {
			val fragment = VideoFragment().apply {
				// 从首页带视频进入时携带参数定位首个视频；否则走随机视频流
				if (intent.hasExtra(ExtraKeys.EXTRA_VIDEO)) arguments = intent.extras
			}
			supportFragmentManager.commit {
				replace(R.id.video_container, fragment)
			}
		}
	}
}
