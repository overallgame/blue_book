package com.example.blue_book.room.provider

import com.example.blue_book.AppContext
import com.example.blue_book.provider.IUserDataProvider
import com.example.blue_book.room.user.UserLocalDataResource
import com.therouter.inject.ServiceProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataServiceEntryPoint {
    fun userLocalDataResource(): UserLocalDataResource
}

@ServiceProvider(returnType = IUserDataProvider::class)
fun provideUserDataProvider(): IUserDataProvider {
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        AppContext.application, DataServiceEntryPoint::class.java
    )
    return UserDataProviderImpl(entryPoint.userLocalDataResource())
}
