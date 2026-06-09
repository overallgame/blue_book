package com.example.blue_book.di

import com.example.blue_book.room.user.UserLocalDataResource
import com.example.blue_book.room.user.UserLocalDataResourceImpl
import com.example.blue_book.datastore.AuthDataStoreImpl
import com.example.blue_book.preference.AuthPreferences
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

    @Binds
    @Singleton
    abstract fun bindAuthPreferences(impl: AuthDataStoreImpl): AuthPreferences
}

