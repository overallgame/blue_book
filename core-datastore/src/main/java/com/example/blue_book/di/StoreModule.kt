package com.example.blue_book.di

import com.example.blue_book.provider.IUserStore
import com.example.blue_book.room.provider.UserStoreProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [IUserStore] 的接口在 lib-base、实现在本模块。绑进 SingletonComponent 后，
 * 其它模块直接注入接口即可，不需要依赖 core-datastore 的编译产物。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StoreModule {

	@Binds
	@Singleton
	abstract fun bindUserStore(impl: UserStoreProviderImpl): IUserStore
}
