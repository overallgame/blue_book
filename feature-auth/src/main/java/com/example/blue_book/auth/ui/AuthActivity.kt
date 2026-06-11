package com.example.blue_book.auth.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.auth.ui.entry.AuthEntryFragment
import com.example.blue_book.auth.ui.login.LoginFragment
import com.example.blue_book.auth.ui.register.RegisterFragment
import com.example.blue_book.feature_auth.R
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint

@Route(path = RoutePath.AUTH)
@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.auth_container, AuthEntryFragment())
            }
        }
    }

    fun navigateToLogin() {
        supportFragmentManager.commit {
            replace(R.id.auth_container, LoginFragment())
            addToBackStack("login")
        }
    }

    fun navigateToRegister() {
        supportFragmentManager.commit {
            replace(R.id.auth_container, RegisterFragment())
            addToBackStack("register")
        }
    }

    fun navigateToHome() {
        TheRouter.build(RoutePath.MAIN)
            .withFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            .navigation(this)
        finish()
    }
}
