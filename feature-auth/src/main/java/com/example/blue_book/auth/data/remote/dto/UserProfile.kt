package com.example.blue_book.auth.data.remote.dto

data class UserProfile(
    val phone: String?,
    val avatar: String?,
    val nickname: String?,
    val password: String?,
    val bio: String?,
    val gender: String?,
    val birthday: String?,
    val occupation: String?,
    val region: String?,
    val school: String?,
    val backgroundImage: String?,
    val token: String? = null,
    val refreshToken: String? = null
)
