package com.example.bluebook.notification.dto

data class NotificationDto(
    val id: Long,
    /** LIKE / COMMENT / COLLECT / FOLLOW / SYSTEM */
    val type: String,
    val senderId: Long,
    val senderNickname: String,
    val senderAvatar: String,
    val videoId: Long? = null,
    val commentId: Long? = null,
    val content: String = "",
    val isRead: Boolean = false,
    /** epoch millis */
    val createdAt: Long = 0
)

data class NotificationListDto(
    val items: List<NotificationDto>,
    val hasMore: Boolean = false
)
