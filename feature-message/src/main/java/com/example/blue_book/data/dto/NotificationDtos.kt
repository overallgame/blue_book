package com.example.blue_book.data.dto

import com.google.gson.annotations.SerializedName

data class NotificationDto(
	@SerializedName("id") val id: Long = 0,
	@SerializedName("type") val type: String = "",
	@SerializedName("senderId") val senderId: Long = 0,
	@SerializedName("senderNickname") val senderNickname: String = "",
	@SerializedName("senderAvatar") val senderAvatar: String = "",
	@SerializedName("videoId") val videoId: Long? = null,
	@SerializedName("commentId") val commentId: Long? = null,
	@SerializedName("content") val content: String = "",
	@SerializedName("isRead") val isRead: Boolean = false,
	@SerializedName("createdAt") val createdAt: Long = 0
)

data class NotificationListDto(
	@SerializedName("items") val items: List<NotificationDto> = emptyList(),
	@SerializedName("hasMore") val hasMore: Boolean = false
)
