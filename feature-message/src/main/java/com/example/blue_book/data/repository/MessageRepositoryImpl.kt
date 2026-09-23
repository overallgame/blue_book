package com.example.blue_book.data.repository

import com.example.blue_book.data.mapper.toDomain
import com.example.blue_book.data.remote.MessageRemoteDataSource
import com.example.blue_book.domain.model.NotificationPage
import com.example.blue_book.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
	private val remote: MessageRemoteDataSource
) : MessageRepository {

	override suspend fun list(cursorId: Long?, size: Int?): Result<NotificationPage> =
		remote.list(cursorId, size).map { it.toDomain() }

	override suspend fun unreadCount(): Result<Long> = remote.unreadCount()

	override suspend fun markRead(id: Long): Result<Unit> = remote.markRead(id)

	override suspend fun delete(id: Long): Result<Unit> = remote.delete(id)

	override suspend fun clearAll(): Result<Unit> = remote.clearAll()
}
