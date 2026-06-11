package com.example.blue_book.auth.data.remote

import com.example.blue_book.auth.data.remote.dto.LoginRequest
import com.example.blue_book.auth.data.remote.dto.LoginResponse
import com.example.blue_book.auth.data.remote.dto.RegisterRequest
import com.example.blue_book.auth.data.remote.dto.RegisterResponse
import com.example.blue_book.auth.data.remote.dto.SendCodeRequest
import com.example.blue_book.network.data.ApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("/api/v2/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginResponse>>

    @POST("/api/v2/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<RegisterResponse>>

    @POST("/api/v2/auth/code")
    suspend fun sendVerificationCode(
        @Body request: SendCodeRequest
    ): Response<ApiResponse<String>>
}
