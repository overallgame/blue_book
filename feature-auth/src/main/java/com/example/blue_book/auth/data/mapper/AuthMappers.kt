package com.example.blue_book.auth.data.mapper

import com.example.blue_book.auth.data.remote.dto.UserProfile
import com.example.blue_book.data.UserAccount
import com.example.blue_book.network.ApiGateway

fun UserProfile.toDomain(): UserAccount {
	val base = ApiGateway.BASE_URL.trimEnd('/')
	fun abs(url: String?): String? {
		val v = url?.trim().orEmpty()
		if (v.isBlank()) return null
		if (v.startsWith("http://") || v.startsWith("https://")) return v
		return if (v.startsWith("/")) "$base$v" else "$base/$v"
	}
	fun n(s: String?): String? = s?.trim()?.ifBlank { null }
	return UserAccount(
		id = id ?: 0,
		phone = phone.orEmpty(),
		avatar = abs(avatar),
		nickname = n(nickname),
		password = password,
		introduction = n(bio),
		sex = n(gender),
		birthday = n(birthday),
		career = n(occupation),
		region = n(region),
		school = n(school),
		background = abs(background),
		followerCount = followerCount,
		followingCount = followingCount
	)
}

