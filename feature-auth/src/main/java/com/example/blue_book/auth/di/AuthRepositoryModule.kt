package com.example.blue_book.auth.di

import com.example.blue_book.auth.data.repository.AuthRepositoryImpl
import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.auth.provider.AuthProviderImpl
import com.example.blue_book.provider.IAuthProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthRepositoryModule {

	@Binds
	@Singleton
	abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

	companion object {

		/** 跨模块服务：接口在 lib-base、实现在本模块，其它模块注入接口即可 */
		@Provides
		@Singleton
		fun provideAuthProvider(repository: AuthRepository): IAuthProvider =
			AuthProviderImpl(repository)
	}
}
