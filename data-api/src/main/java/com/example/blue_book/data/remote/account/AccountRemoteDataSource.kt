package com.example.blue_book.data.remote.account

import com.example.blue_book.data.remote.account.dto.AuthV2CodeRequestDto
import com.example.blue_book.data.remote.account.dto.AuthV2RegisterRequestDto
import com.example.blue_book.data.remote.account.dto.LoginRequestDto
import com.example.blue_book.data.remote.account.dto.LoginResponseDto
import com.example.blue_book.data.remote.account.dto.RegisterResponseDto
import com.example.blue_book.data.remote.apiCall
import javax.inject.Inject

class AccountRemoteDataSource @Inject constructor(
    private val accountApi: AccountApi
) {

    suspend fun login(request: LoginRequestDto): Result<LoginResponseDto> {
        return apiCall { accountApi.login(request) }
    }

    suspend fun register(
        nickname: String,
        phone: String,
        password: String,
        code: String
    ): Result<RegisterResponseDto> {
        return apiCall {
            accountApi.register(
                AuthV2RegisterRequestDto(
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
            accountApi.sendVerificationCode(
                AuthV2CodeRequestDto(phone = phone, nickname = nickname)
            )
        }
    }
}
