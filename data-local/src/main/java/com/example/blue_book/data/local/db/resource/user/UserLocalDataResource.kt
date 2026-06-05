package com.example.blue_book.data.local.db.resource.user

import com.example.blue_book.data.local.db.entity.UserEntity

interface UserLocalDataResource {

    suspend fun saveUser(userEntity: UserEntity)

    suspend fun updateUser(userEntity: UserEntity)

    suspend fun getCurrentUser(phone: String): UserEntity?

    suspend fun clearUser()

    fun getCurrentUserPhone(): String?

}