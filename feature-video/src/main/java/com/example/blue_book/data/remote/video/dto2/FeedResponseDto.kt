package com.example.blue_book.data.remote.video.dto2

data class FeedResponseDto(
	val items: List<Video2Dto>,
	val nextCursorId: Long?
)
