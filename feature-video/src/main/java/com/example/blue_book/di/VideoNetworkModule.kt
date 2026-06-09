package com.example.blue_book.di

import com.example.blue_book.data.remote.comment.CommentApi
import com.example.blue_book.data.remote.video.VideoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VideoNetworkModule {

    @Provides @Singleton
    fun provideVideoApi(@Named("backend") retrofit: Retrofit): VideoApi =
        retrofit.create(VideoApi::class.java)

    @Provides @Singleton
    fun provideCommentApi(@Named("backend") retrofit: Retrofit): CommentApi =
        retrofit.create(CommentApi::class.java)
}
