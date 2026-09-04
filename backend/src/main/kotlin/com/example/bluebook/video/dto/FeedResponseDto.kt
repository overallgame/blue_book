package com.example.bluebook.video.dto

data class FeedResponseDto(
    val items: List<Video2Dto>,
    val nextCursorId: Long?
)
