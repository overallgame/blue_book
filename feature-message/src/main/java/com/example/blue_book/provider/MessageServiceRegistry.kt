package com.example.blue_book.provider

import com.example.blue_book.AppContext
import com.example.blue_book.data.remote.MessageRemoteDataSource
import com.therouter.inject.ServiceProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MessageServiceEntryPoint {
	fun messageRemoteDataSource(): MessageRemoteDataSource
}

@ServiceProvider(returnType = INotificationProvider::class)
fun provideNotificationProvider(): INotificationProvider {
	val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
		AppContext.application, MessageServiceEntryPoint::class.java
	)
	return NotificationProviderImpl(entryPoint.messageRemoteDataSource())
}
