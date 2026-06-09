package com.example.blue_book.di

import com.example.blue_book.data.remote.account.AccountApi
import com.example.blue_book.data.remote.file.FileApi
import com.example.blue_book.data.remote.user.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MineNetworkModule {

    @Provides @Singleton
    fun provideAccountApi(@Named("backend") retrofit: Retrofit): AccountApi =
        retrofit.create(AccountApi::class.java)

    @Provides @Singleton
    fun provideUserApi(@Named("backend") retrofit: Retrofit): UserApi =
        retrofit.create(UserApi::class.java)

    @Provides @Singleton
    fun provideFileApi(@Named("backend") retrofit: Retrofit): FileApi =
        retrofit.create(FileApi::class.java)
}
