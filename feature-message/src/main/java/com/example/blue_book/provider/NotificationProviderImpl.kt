package com.example.blue_book.provider

import com.example.blue_book.data.remote.MessageRemoteDataSource

class NotificationProviderImpl(
	private val remote: MessageRemoteDataSource
) : INotificationProvider {

	/** 未登录时后端返回 401，调用方静默忽略即可 */
	override suspend fun unreadCount(): Result<Long> = remote.unreadCount()
}
