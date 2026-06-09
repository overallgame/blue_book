package com.example.blue_book.data.remote.account.dto

data class RegisterResponseDto(
    val token: String?,
    val refreshToken: String?,
    val profile: UserProfileDto?
)


