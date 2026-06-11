package com.example.blue_book.provider

import com.example.blue_book.data.UserAccount

/**
 * 用户本地数据服务接口 — 由 core-datastore 模块提供。
 */
interface IUserDataProvider {

    suspend fun saveUser(user: UserAccount)

    suspend fun updateUser(user: UserAccount)

    suspend fun getUserByPhone(phone: String): UserAccount?

    suspend fun clearUser()

    fun getCurrentUserPhone(): String?
}
