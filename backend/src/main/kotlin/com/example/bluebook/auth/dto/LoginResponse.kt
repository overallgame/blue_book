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
    val followerCount: Long,
    val followingCount: Long
)
