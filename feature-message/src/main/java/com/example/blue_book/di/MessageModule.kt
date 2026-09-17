package com.example.blue_book.di

import com.example.blue_book.data.remote.MessageRemoteDataSource
import com.example.blue_book.provider.INotificationProvider
import com.example.blue_book.provider.NotificationProviderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [INotificationProvider] 的接口在 lib-base、实现在本模块。
 * 绑进 SingletonComponent 后，其它模块（:app 的未读角标）直接注入接口即可。
 */
@Module
@InstallIn(SingletonComponent::class)
object MessageModule {

	@Provides
	@Singleton
	fun provideNotificationProvider(remote: MessageRemoteDataSource): INotificationProvider =
		NotificationProviderImpl(remote)
}
