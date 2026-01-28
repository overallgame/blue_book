package com.example.blue_book.data.local.preference

interface AuthPreferences {
    fun setPhone(phone: String)

    fun getPhone(): String?

    fun setAuthToken(token: String?)

    fun getAuthToken(): String?

    fun setRefreshToken(token: String?)

    fun getRefreshToken(): String?

    fun clear()

}