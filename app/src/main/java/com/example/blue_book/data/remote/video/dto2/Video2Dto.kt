package com.example.blue_book.data.remote.video.dto2

data class Video2Dto(
	val videoId: Long,
	val uploaderId: Long,
	val uploaderNickname: String,
	val uploaderAvatar: String,
	val title: String,
	val description: String,
	val coverUrl: String,
	val videoUrl: String,
	val likeCount: Long,
	val collectCount: Long,
	val viewCount: Long,
	val commentCount: Long,
	val isLike: Boolean,
	val isCollect: Boolean
)
