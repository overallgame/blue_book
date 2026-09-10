package com.example.blue_book.data.remote.user.dto2

data class UserV2MeDto(
	val id: Long,
	val phone: String,
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
	val likedCount: Long = 0,
	val collectedCount: Long = 0
)
