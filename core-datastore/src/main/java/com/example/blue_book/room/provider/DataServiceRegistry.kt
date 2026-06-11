package com.example.blue_book.room.provider

import com.example.blue_book.provider.IUserDataProvider
import com.therouter.inject.ServiceProvider

/**
 * TheRouter 服务注册：将 Hilt 管理的 IUserDataProvider 实例暴露给其他模块。
 */
object DataServiceRegistry {

    @JvmStatic
    @ServiceProvider(returnType = IUserDataProvider::class)
    fun provideUserDataProvider(): IUserDataProvider {
        return UserDataProviderImpl.instance
            ?: throw IllegalStateException("UserDataProviderImpl has not been initialized by Hilt yet")
    }
}
