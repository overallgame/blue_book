package com.example.blue_book.data.remote.user.dto2

data class UserV2FollowListResponseDto(
	val items: List<UserV2ProfileDto>,
	val nextCursorId: Long?
)
