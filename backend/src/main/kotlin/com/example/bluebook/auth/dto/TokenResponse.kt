package com.example.bluebook.auth.dto

data class TokenResponse(
    val token: String?,
    val refreshToken: String?
)
