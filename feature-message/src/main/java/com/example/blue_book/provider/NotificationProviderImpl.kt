package com.example.blue_book.provider

import com.example.blue_book.domain.repository.MessageRepository

class NotificationProviderImpl(
	private val repository: MessageRepository
) : INotificationProvider {

	/** 未登录时后端返回 401，调用方静默忽略即可 */
	override suspend fun unreadCount(): Result<Long> = repository.unreadCount()
}
