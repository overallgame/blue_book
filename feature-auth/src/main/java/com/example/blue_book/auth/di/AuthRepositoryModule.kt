package com.example.blue_book.auth.di

import com.example.blue_book.auth.data.repository.AuthRepositoryImpl
import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.auth.provider.AuthProviderImpl
import com.example.blue_book.provider.IAuthProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAuthProvider(impl: AuthProviderImpl): IAuthProvider
}
