package com.example.blue_book.provider

import com.therouter.inject.ServiceProvider

/**
 * TheRouter 服务注册：将 Hilt 管理的 IVideoProvider 实例暴露给其他模块。
 */
object VideoServiceRegistry {

    @JvmStatic
    @ServiceProvider(returnType = IVideoProvider::class)
    fun provideVideoProvider(): IVideoProvider {
        return VideoProviderImpl.instance
            ?: throw IllegalStateException("VideoProviderImpl has not been initialized by Hilt yet")
    }
}
