package com.example.blue_book.provider

import com.example.blue_book.AppContext
import com.example.blue_book.domain.repository.VideoRepository
import com.therouter.inject.ServiceProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoServiceEntryPoint {
    fun videoRepository(): VideoRepository
}

@ServiceProvider(returnType = IVideoProvider::class)
fun provideVideoProvider(): IVideoProvider {
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        AppContext.application, VideoServiceEntryPoint::class.java
    )
    return VideoProviderImpl(entryPoint.videoRepository())
}
