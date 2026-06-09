package com.example.blue_book.network.api

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.network.dto.RefreshTokenRequest
import com.example.blue_book.network.dto.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TokenApi {

    @POST("/api/v2/auth/refresh")
    suspend fun refresh(
        @Body body: RefreshTokenRequest
    ): Response<ApiResponse<TokenResponse>>

    @POST("/api/v2/auth/logout")
    suspend fun logout(): Response<ApiResponse<Any>>
}
