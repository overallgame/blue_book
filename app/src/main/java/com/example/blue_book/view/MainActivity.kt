package com.example.blue_book.view

import android.content.Intent
import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.blue_book.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_bottom_nav)

        val radioGroup = findViewById<RadioGroup>(R.id.main_navRadioGroup)

        if (savedInstanceState == null) {
            radioGroup.check(R.id.tab_home)
            navigateToTab("com.example.blue_book.presentation.home.HomeActivity")
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val targetClass = when (checkedId) {
                R.id.tab_home -> "com.example.blue_book.presentation.home.HomeActivity"
                R.id.tab_video -> "com.example.blue_book.presentation.video.VideoActivity"
                R.id.tab_message -> "com.example.blue_book.presentation.message.MessageActivity"
                R.id.tab_mine -> "com.example.blue_book.presentation.mine.MineActivity"
                else -> return@setOnCheckedChangeListener
            }
            navigateToTab(targetClass)
        }
    }

    private fun navigateToTab(className: String) {
        val intent = Intent().apply {
            setClassName(packageName, className)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }
}
