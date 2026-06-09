package com.example.blue_book.auth.data.remote.dto

data class RegisterResponse(
    val token: String?,
    val refreshToken: String?,
    val profile: UserProfile?
)
