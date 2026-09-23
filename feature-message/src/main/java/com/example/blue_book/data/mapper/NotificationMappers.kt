package com.example.blue_book.data.mapper

import com.example.blue_book.data.dto.NotificationDto
import com.example.blue_book.data.dto.NotificationListDto
import com.example.blue_book.domain.model.Notification
import com.example.blue_book.domain.model.NotificationPage
import com.example.blue_book.network.ApiGateway
import com.example.blue_book.util.absoluteUrl

/**
 * 通知 DTO → domain。
 *
 * 头像要**绝对化**：服务端下发的是相对路径（`/upload/images/...`），
 * 直接交给 Glide 会加载失败——消息列表上的头像会一直空着，而日志里什么都不会有。
 */
fun NotificationDto.toDomain(): Notification = Notification(
	id = id,
	type = type,
	senderId = senderId,
	avatar = absoluteUrl(ApiGateway.BASE_URL, senderAvatar) ?: "",
	nickname = senderNickname,
	content = content,
	time = createdAt,
	isRead = isRead,
	videoId = videoId
)

fun NotificationListDto.toDomain(): NotificationPage = NotificationPage(
	items = items.map { it.toDomain() },
	hasMore = hasMore
)
