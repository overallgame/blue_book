package com.example.blue_book.data.remote

import com.example.blue_book.data.dto.NotificationListDto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

class MessageRemoteDataSource @Inject constructor(
	private val apiGateway: ApiGateway
) {
	private val api = apiGateway.createApi(MessageApi::class.java)

	suspend fun list(cursorId: Long?, size: Int?): Result<NotificationListDto> =
		apiGateway.apiResult { api.list(cursorId, size) }

	suspend fun unreadCount(): Result<Long> =
		apiGateway.apiResult { api.unreadCount() }

	suspend fun markRead(id: Long): Result<Unit> =
		apiGateway.apiUnitResult { api.markRead(id) }

	suspend fun markAllRead(): Result<Unit> =
		apiGateway.apiUnitResult { api.markAllRead() }
}
