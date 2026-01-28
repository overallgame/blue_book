package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.LoginCredentials
import com.example.blue_book.domain.model.RegisterInfo
import com.example.blue_book.domain.model.UserAccount

interface UserRepository {

    suspend fun isLoggedIn(): Boolean

    suspend fun login(credentials: LoginCredentials): Result<UserAccount>

    suspend fun logout(): Result<Unit>

    suspend fun register(info: RegisterInfo): Result<UserAccount>

    suspend fun sendVerificationCode(phone: String, nickname: String): Result<String>

    suspend fun getUserProfile(phone: String): Result<UserAccount>

    suspend fun updateUserProfile(account: UserAccount): Result<Unit>

    suspend fun currentUserPhone(): String?
}

