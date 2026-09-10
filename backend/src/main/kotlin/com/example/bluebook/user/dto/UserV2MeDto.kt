package com.example.bluebook.user.dto

data class UserV2MeDto(
    val id: Long,
    val phone: String,
    /** 对外"小红书号"（脱敏手机号的替代，不可逆派生） */
    val xhsId: String? = null,
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
    /** 获赞与收藏合计（我的页统计） */
    val likedCount: Long = 0,
    val collectedCount: Long = 0
)
