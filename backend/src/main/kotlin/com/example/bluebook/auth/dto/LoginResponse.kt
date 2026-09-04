package com.example.bluebook.auth.dto

data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val profile: UserProfile?
)

data class UserProfile(
    val id: Long,
    val phone: String,
    val nickname: String,
    val avatar: String?,
    val bio: String?,
    val gender: String?,
    val birthday: String?,
    val occupation: String?,
    val region: String?,
    val school: String?,
    val background: String? = null,
    val password: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val followerCount: Long = 0,
    val followingCount: Long = 0
)
