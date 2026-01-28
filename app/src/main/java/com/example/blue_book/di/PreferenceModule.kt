package com.example.blue_book.di

import com.example.blue_book.data.local.preference.AuthPreferences
import com.example.blue_book.data.local.preference.AuthPreferencesImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferenceModule {

    @Binds
    @Singleton
    abstract fun bindAuthPreferences(impl: AuthPreferencesImpl): AuthPreferences
}

