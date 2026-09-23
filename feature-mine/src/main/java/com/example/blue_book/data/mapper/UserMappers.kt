package com.example.blue_book.data.mapper

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.data.UserAccount
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto
import com.example.blue_book.util.absoluteUrl

fun UserV2ProfileDto.toDomain(phone: String = ""): UserAccount {
	fun n(s: String?): String? {
		val v = s?.trim().orEmpty()
		return v.ifBlank { null }
	}
	fun abs(url: String?): String? = absoluteUrl(ApiGateway.BASE_URL, url)
	return UserAccount(
		id = id,
		phone = phone,
		xhsId = xhsId,
		avatar = abs(avatar),
		nickname = n(nickname),
		password = null,
		introduction = n(bio),
		sex = n(gender),
		birthday = n(birthday),
		career = n(occupation),
		region = n(region),
		school = n(school),
		background = abs(backgroundImage),
		isFollowed = isFollowed,
		followerCount = followerCount,
		followingCount = followingCount,
		likedCount = likedCount,
		collectedCount = collectedCount
	)
}

fun UserV2MeDto.toDomain(): UserAccount {
	fun n(s: String?): String? {
		val v = s?.trim().orEmpty()
		return v.ifBlank { null }
	}
	fun abs(url: String?): String? = absoluteUrl(ApiGateway.BASE_URL, url)
	return UserAccount(
		id = id,
		phone = phone,
		xhsId = xhsId,
		avatar = abs(avatar),
		nickname = n(nickname),
		password = null,
		introduction = n(bio),
		sex = n(gender),
		birthday = n(birthday),
		career = n(occupation),
		region = n(region),
		school = n(school),
		background = abs(backgroundImage),
		followerCount = followerCount,
		followingCount = followingCount,
		likedCount = likedCount,
		collectedCount = collectedCount
	)
}
