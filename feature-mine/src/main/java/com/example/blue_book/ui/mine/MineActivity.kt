package com.example.blue_book.ui.mine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_mine.R
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.profile.UserProfileEditFragment
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.MINE)
@AndroidEntryPoint
class MineActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mine)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.mine_container, MineFragment())
            }
        }
    }

    fun navigateToProfileEdit() {
        supportFragmentManager.commit {
            replace(R.id.mine_container, UserProfileEditFragment())
            addToBackStack("profile_edit")
        }
    }

    fun navigateToAuthEntry() {
        TheRouter.build(RoutePath.AUTH)
            .withFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            .navigation(this)
        finish()
    }
}
