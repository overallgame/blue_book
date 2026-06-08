package com.example.blue_book.presentation.video

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_video.R
import com.example.blue_book.presentation.main.VideoTabFragment
import com.example.blue_book.router.RouterPath
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoActivity : AppCompatActivity() {
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
