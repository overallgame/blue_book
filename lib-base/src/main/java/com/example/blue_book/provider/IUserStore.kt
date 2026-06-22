package com.example.blue_book.provider

import com.example.blue_book.data.UserAccount

/**
 * 用户本地存储接口 — 由 core-datastore 提供实现。
 * 封装 Room UserDao + TokenHolder 的操作，只暴露 UserAccount 类型。
 */
interface IUserStore {

    suspend fun saveUser(account: UserAccount)

    suspend fun updateUser(account: UserAccount)

    suspend fun getUserByPhone(phone: String): UserAccount?

    suspend fun deleteUserByPhone(phone: String)
}
