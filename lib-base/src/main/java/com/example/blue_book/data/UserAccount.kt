package com.example.blue_book.data

data class UserAccount(
	val id: Long = 0,
	val phone: String,
	val avatar: String?,
	val nickname: String?,
	val password: String?,
	val introduction: String?,
	val sex: String?,
	val birthday: String?,
	val career: String?,
	val region: String?,
	val school: String?,
	val background: String?,
	val isFollowed: Boolean = false,
	/** 粉丝数 / 关注数（我的页统计展示） */
	val followerCount: Long = 0,
	val followingCount: Long = 0
)

