package com.example.blue_book.data.remote.user.dto2

data class UserV2UpdateRequestDto(
	val nickname: String?,
	val bio: String?,
	val gender: String?,
	val birthday: String?,
	val occupation: String?,
	val region: String?,
	val school: String?,
	val backgroundImage: String?
)
