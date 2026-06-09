package com.example.blue_book.auth.di

import com.example.blue_book.auth.data.remote.AuthApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthNetworkModule {

    @Provides @Singleton
    fun provideAuthApi(@Named("backend") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)
}
