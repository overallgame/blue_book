package com.example.blue_book.di

import com.example.blue_book.data.local.db.resource.user.UserLocalDataResource
import com.example.blue_book.data.local.db.resource.user.UserLocalDataResourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalModule {

    @Binds
    @Singleton
    abstract fun bindUserLocalDataResource(impl: UserLocalDataResourceImpl): UserLocalDataResource
}

