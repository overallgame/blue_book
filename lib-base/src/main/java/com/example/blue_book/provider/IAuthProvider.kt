package com.example.blue_book.provider

/**
 * 认证服务接口 — 由 feature-auth 模块提供
 */
interface IAuthProvider {

    suspend fun isLoggedIn(): Boolean

    suspend fun logout(): Result<Unit>
}
