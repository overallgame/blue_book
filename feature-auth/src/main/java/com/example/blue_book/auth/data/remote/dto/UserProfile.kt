package com.example.blue_book.auth.data.remote.dto

/**
 * 登录/注册响应里的用户资料。
 * 字段名与后端 auth.UserProfile 对齐：背景图字段是 background，
 * 与 /api/v2/me 系列接口的 backgroundImage 不同名，写错会静默丢字段。
 */
data class UserProfile(
	val id: Long? = null,
	val phone: String?,
	val avatar: String?,
	val nickname: String?,
	val password: String?,
	val bio: String?,
	val gender: String?,
	val birthday: String?,
	val occupation: String?,
	val region: String?,
	val school: String?,
	val background: String? = null,
	val token: String? = null,
	val refreshToken: String? = null,
	val followerCount: Long = 0,
	val followingCount: Long = 0
)
