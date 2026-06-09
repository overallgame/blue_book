package com.example.blue_book.auth.domain.repository

import com.example.blue_book.auth.domain.model.LoginCredentials
import com.example.blue_book.auth.domain.model.RegisterInfo
import com.example.blue_book.common.bean.UserAccount

interface AuthRepository {

    suspend fun isLoggedIn(): Boolean

    suspend fun login(credentials: LoginCredentials): Result<UserAccount>

    suspend fun logout(): Result<Unit>

    suspend fun register(info: RegisterInfo): Result<UserAccount>

    suspend fun sendVerificationCode(phone: String, nickname: String): Result<String>
}
