package com.example.blue_book.auth.data.remote

import com.example.blue_book.auth.data.remote.dto.LoginRequest
import com.example.blue_book.auth.data.remote.dto.LoginResponse
import com.example.blue_book.auth.data.remote.dto.RegisterRequest
import com.example.blue_book.auth.data.remote.dto.RegisterResponse
import com.example.blue_book.auth.data.remote.dto.SendCodeRequest
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

class AuthRemoteDataSource @Inject constructor(
    private val apiGateway: ApiGateway
) {
    private val authApi = apiGateway.createApi(AuthApi::class.java)

    suspend fun login(request: LoginRequest): Result<LoginResponse> {
        return apiGateway.apiResult { authApi.login(request) }
    }

    suspend fun register(
        nickname: String,
        phone: String,
        password: String,
        code: String
    ): Result<RegisterResponse> {
        return apiGateway.apiResult {
            authApi.register(
                RegisterRequest(
                    nickname = nickname,
                    phone = phone,
                    password = password,
                    code = code
                )
            )
        }
    }

    suspend fun sendVerificationCode(phone: String, nickname: String): Result<String> {
        return apiGateway.apiResult {
            authApi.sendVerificationCode(
                SendCodeRequest(phone = phone, nickname = nickname)
            )
        }
    }
}
