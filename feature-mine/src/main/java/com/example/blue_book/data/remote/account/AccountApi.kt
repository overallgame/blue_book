package com.example.blue_book.data.remote.account

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.remote.account.dto.AuthV2CodeRequestDto
import com.example.blue_book.data.remote.account.dto.AuthV2RegisterRequestDto
import com.example.blue_book.data.remote.account.dto.LoginRequestDto
import com.example.blue_book.data.remote.account.dto.LoginResponseDto
import com.example.blue_book.data.remote.account.dto.RegisterResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AccountApi {

    @POST("/api/v2/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<ApiResponse<LoginResponseDto>>

    @POST("/api/v2/auth/register")
    suspend fun register(
        @Body request: AuthV2RegisterRequestDto
    ): Response<ApiResponse<RegisterResponseDto>>

    @POST("/api/v2/auth/code")
    suspend fun sendVerificationCode(
        @Body request: AuthV2CodeRequestDto
    ): Response<ApiResponse<String>>
}

