package com.example.blue_book.auth.provider

import com.example.blue_book.AppContext
import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.provider.IAuthProvider
import com.therouter.inject.ServiceProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * TheRouter IProvider 注册 —— 方案B：纯服务发现模式。
 * 通过 Hilt EntryPoint 获取 AuthRepository，消除静态持有者。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthServiceEntryPoint {
    fun authRepository(): AuthRepository
}

@ServiceProvider(returnType = IAuthProvider::class)
fun provideAuthProvider(): IAuthProvider {
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        AppContext.application, AuthServiceEntryPoint::class.java
    )
    return AuthProviderImpl(entryPoint.authRepository())
}
