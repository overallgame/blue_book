package com.example.blue_book.data.remote.user

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.remote.user.dto2.UserV2AvatarUploadResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2FollowListResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApi {

	@GET("/api/v2/me")
	suspend fun me(): Response<ApiResponse<UserV2MeDto>>

	@PUT("/api/v2/me")
	suspend fun updateMe(
		@Body body: UserV2UpdateRequestDto
	): Response<ApiResponse<UserV2MeDto>>

	@Multipart
	@POST("/api/v2/me/avatar")
	suspend fun uploadAvatar(
		@Part avatar: MultipartBody.Part
	): Response<ApiResponse<UserV2AvatarUploadResponseDto>>

	@GET("/api/v2/users/{userId}")
	suspend fun profile(
		@Path("userId") userId: Long
	): Response<ApiResponse<UserV2ProfileDto>>

	@POST("/api/v2/users/{userId}/follow")
	suspend fun follow(
		@Path("userId") userId: Long
	): Response<ApiResponse<Any>>

	@DELETE("/api/v2/users/{userId}/follow")
	suspend fun unfollow(
		@Path("userId") userId: Long
	): Response<ApiResponse<Any>>

	@GET("/api/v2/users/{userId}/followers")
	suspend fun followers(
		@Path("userId") userId: Long,
		@Query("cursorId") cursorId: Long? = null,
		@Query("size") size: Int? = null
	): Response<ApiResponse<UserV2FollowListResponseDto>>

	@GET("/api/v2/users/{userId}/following")
	suspend fun following(
		@Path("userId") userId: Long,
		@Query("cursorId") cursorId: Long? = null,
		@Query("size") size: Int? = null
	): Response<ApiResponse<UserV2FollowListResponseDto>>
}
