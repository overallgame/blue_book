package com.example.blue_book.auth.data.remote

import com.example.blue_book.auth.data.remote.dto.LoginRequest
import com.example.blue_book.auth.data.remote.dto.LoginResponse
import com.example.blue_book.auth.data.remote.dto.RegisterRequest
import com.example.blue_book.auth.data.remote.dto.RegisterResponse
import com.example.blue_book.auth.data.remote.dto.SendCodeRequest
import com.example.blue_book.network.apiCall
import javax.inject.Inject

class AuthRemoteDataSource @Inject constructor(
    private val authApi: AuthApi
) {

    suspend fun login(request: LoginRequest): Result<LoginResponse> {
        return apiCall { authApi.login(request) }
    }

    suspend fun register(
        nickname: String,
        phone: String,
        password: String,
        code: String
    ): Result<RegisterResponse> {
        return apiCall {
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
        return apiCall {
            authApi.sendVerificationCode(
                SendCodeRequest(phone = phone, nickname = nickname)
            )
        }
    }
}
