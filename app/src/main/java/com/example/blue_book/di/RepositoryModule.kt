package com.example.blue_book.di

import com.example.blue_book.data.repository.UserRepositoryImpl
import com.example.blue_book.data.repository.VideoRepositoryImpl
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}

