package com.example.blue_book.data.remote.auth

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.remote.auth.dto2.AuthV2RefreshRequestDto
import com.example.blue_book.data.remote.auth.dto2.AuthV2TokenResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

	@POST("/api/v2/auth/refresh")
	suspend fun refresh(
		@Body body: AuthV2RefreshRequestDto
	): Response<ApiResponse<AuthV2TokenResponseDto>>

	@POST("/api/v2/auth/logout")
	suspend fun logout(): Response<ApiResponse<Any>>
}
