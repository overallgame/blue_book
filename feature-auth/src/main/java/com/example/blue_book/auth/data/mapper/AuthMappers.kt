package com.example.blue_book.auth.data.mapper

import com.example.blue_book.auth.data.remote.dto.UserProfile
import com.example.blue_book.data.UserAccount
import com.example.blue_book.network.ApiGateway
import com.example.blue_book.util.absoluteUrl

fun UserProfile.toDomain(): UserAccount {
	fun n(s: String?): String? = s?.trim()?.ifBlank { null }
	fun abs(url: String?): String? = absoluteUrl(ApiGateway.BASE_URL, url)
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

