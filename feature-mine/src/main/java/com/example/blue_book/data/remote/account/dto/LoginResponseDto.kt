package com.example.blue_book.data.remote.account.dto

data class LoginResponseDto(
    val token: String?,
    val refreshToken: String?,
    val profile: UserProfileDto?
)


