package com.example.bluebook.user.dto

data class UserV2ProfileDto(
    val id: Long,
    val nickname: String,
    val avatar: String?,
    val backgroundImage: String?,
    val bio: String?,
    val gender: String?,
    val birthday: String?,
    val occupation: String?,
    val region: String?,
    val school: String?,
    val followerCount: Long,
    val followingCount: Long,
    val isFollowed: Boolean
)
