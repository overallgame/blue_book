package com.example.blue_book.ui.video

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.media3.common.util.UnstableApi
import com.example.blue_book.feature_video.R
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.main.VideoTabFragment
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
            val hasVideo = intent.hasExtra("EXTRA_VIDEO")
            val fragment = if (hasVideo) {
                VideoFragment().apply { arguments = intent.extras }
            } else {
                VideoTabFragment()
            }
            supportFragmentManager.commit {
                replace(R.id.video_container, fragment)
            }
        }
    }
}
