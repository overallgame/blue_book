package com.example.blue_book.auth.data.remote.dto

data class LoginResponse(
    val token: String?,
    val refreshToken: String?,
    val profile: UserProfile?
)
