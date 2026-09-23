package com.example.blue_book.di

import com.example.blue_book.provider.IUploadSessionStore
import com.example.blue_book.provider.IUserStore
import com.example.blue_book.room.provider.UploadSessionStoreImpl
import com.example.blue_book.room.provider.UserStoreProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * lib-base 里的这些接口（[IUserStore]、[IUploadSessionStore]）实现在本模块。
 * 绑进 SingletonComponent 后，其它模块直接注入接口即可，
 * 不需要依赖 core-datastore 的编译产物。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StoreModule {

	@Binds
	@Singleton
	abstract fun bindUserStore(impl: UserStoreProviderImpl): IUserStore

	/** 本地上传会话/分片账本（`feature-video` 消费，用于跨进程续传与指纹缓存） */
	@Binds
	@Singleton
	abstract fun bindUploadSessionStore(impl: UploadSessionStoreImpl): IUploadSessionStore
}
