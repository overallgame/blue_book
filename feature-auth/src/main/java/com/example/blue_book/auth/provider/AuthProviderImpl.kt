package com.example.blue_book.auth.provider

import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.provider.IAuthProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IAuthProvider 的 Hilt 管理实现。
 * 通过静态持有者向 TheRouter 暴露实例。
 */
@Singleton
class AuthProviderImpl @Inject constructor(
    private val authRepository: AuthRepository
) : IAuthProvider {

    override suspend fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    override suspend fun logout(): Result<Unit> = authRepository.logout()

    companion object {
        @Volatile
        var instance: IAuthProvider? = null
    }

    init {
        instance = this
    }
}
