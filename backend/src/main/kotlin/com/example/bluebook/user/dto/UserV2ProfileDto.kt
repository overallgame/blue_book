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
    val isFollowed: Boolean,
    /** 获赞与收藏合计（作者主页统计） */
    val likedCount: Long = 0,
    val collectedCount: Long = 0
)
