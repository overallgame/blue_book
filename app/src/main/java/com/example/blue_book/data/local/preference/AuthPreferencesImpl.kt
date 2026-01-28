package com.example.blue_book.data.local.preference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferencesImpl @Inject constructor(
    @ApplicationContext context: Context
): AuthPreferences {

    private val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    override fun setPhone(phone: String) {
        sharedPreferences.edit().putString("_phone", phone).apply()
    }

    override fun getPhone(): String? {
        return sharedPreferences.getString("_phone", null)
    }

    override fun setAuthToken(token: String?) {
        sharedPreferences.edit().putString("_auth_token", token).apply()
    }

    override fun getAuthToken(): String? {
        return sharedPreferences.getString("_auth_token", null)
    }

    override fun setRefreshToken(token: String?) {
        sharedPreferences.edit().putString("_refresh_token", token).apply()
    }

    override fun getRefreshToken(): String? {
        return sharedPreferences.getString("_refresh_token", null)
    }

    override fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}