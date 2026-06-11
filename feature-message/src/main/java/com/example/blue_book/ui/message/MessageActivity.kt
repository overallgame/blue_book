package com.example.blue_book.ui.message

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_message.R
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.MESSAGE)
@AndroidEntryPoint
class MessageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.message_container, MessageFragment())
            }
        }
    }
}
