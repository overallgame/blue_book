package com.example.blue_book.auth.provider

import com.example.blue_book.provider.IAuthProvider
import com.therouter.inject.ServiceProvider

/**
 * TheRouter 服务注册：将 Hilt 管理的 IAuthProvider 实例暴露给其他模块。
 */
object AuthServiceRegistry {

    @JvmStatic
    @ServiceProvider(returnType = IAuthProvider::class)
    fun provideAuthProvider(): IAuthProvider {
        return AuthProviderImpl.instance
            ?: throw IllegalStateException("AuthProviderImpl has not been initialized by Hilt yet")
    }
}
