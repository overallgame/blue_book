package com.example.blue_book.presentation.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_auth.R
import com.example.blue_book.presentation.auth.entry.AuthEntryFragment
import com.example.blue_book.presentation.auth.login.LoginFragment
import com.example.blue_book.presentation.auth.register.RegisterFragment
import dagger.hilt.android.AndroidEntryPoint

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
        val intent = Intent().apply {
            setClassName(packageName, "com.example.blue_book.view.MainActivity")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
