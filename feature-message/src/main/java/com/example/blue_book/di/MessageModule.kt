package com.example.blue_book.di

import com.example.blue_book.data.repository.MessageRepositoryImpl
import com.example.blue_book.domain.repository.MessageRepository
import com.example.blue_book.provider.INotificationProvider
import com.example.blue_book.provider.NotificationProviderImpl
import dagger.Binds
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
abstract class MessageModule {

	@Binds
	@Singleton
	abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

	companion object {

		@Provides
		@Singleton
		fun provideNotificationProvider(repository: MessageRepository): INotificationProvider =
			NotificationProviderImpl(repository)
	}
}
