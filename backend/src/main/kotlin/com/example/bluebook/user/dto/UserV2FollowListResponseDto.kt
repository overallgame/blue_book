package com.example.bluebook.user.dto

data class UserV2FollowListResponseDto(
    val items: List<UserV2ProfileDto>,
    val nextCursorId: Long?
)
