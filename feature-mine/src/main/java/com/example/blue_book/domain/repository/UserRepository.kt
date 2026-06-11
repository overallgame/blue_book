package com.example.blue_book.domain.repository

import com.example.blue_book.data.UserAccount

interface UserRepository {

    suspend fun getUserProfile(phone: String): Result<UserAccount>

    suspend fun updateUserProfile(account: UserAccount): Result<Unit>

    suspend fun currentUserPhone(): String?
}
