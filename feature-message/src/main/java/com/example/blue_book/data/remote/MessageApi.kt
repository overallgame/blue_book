package com.example.blue_book.data.remote

import com.example.blue_book.data.dto.NotificationListDto
import com.example.blue_book.network.data.ApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MessageApi {

	@GET("/api/v2/notifications")
	suspend fun list(
		@Query("cursorId") cursorId: Long? = null,
		@Query("size") size: Int? = null
	): Response<ApiResponse<NotificationListDto>>

	@GET("/api/v2/notifications/unread-count")
	suspend fun unreadCount(): Response<ApiResponse<Long>>

	@POST("/api/v2/notifications/{id}/read")
	suspend fun markRead(
		@Path("id") id: Long
	): Response<ApiResponse<Any>>

	@POST("/api/v2/notifications/read-all")
	suspend fun markAllRead(): Response<ApiResponse<Any>>
}
