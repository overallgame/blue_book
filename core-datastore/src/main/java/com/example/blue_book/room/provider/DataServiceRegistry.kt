package com.example.blue_book.room.provider

import com.example.blue_book.AppContext
import com.example.blue_book.provider.IUserStore
import com.therouter.inject.ServiceProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StoreProviderEntryPoint {
    fun userStore(): UserStoreProviderImpl
}

@ServiceProvider(returnType = IUserStore::class)
fun provideUserStore(): IUserStore {
    return dagger.hilt.android.EntryPointAccessors.fromApplication(
        AppContext.application, StoreProviderEntryPoint::class.java
    ).userStore()
}
