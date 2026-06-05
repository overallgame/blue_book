package com.example.blue_book.di

import com.example.blue_book.core.network.CoreNetworkModule
import com.example.blue_book.data.remote.account.AccountApi
import com.example.blue_book.data.remote.auth.AuthApi
import com.example.blue_book.data.remote.comment.CommentApi
import com.example.blue_book.data.remote.file.FileApi
import com.example.blue_book.data.remote.user.UserApi
import com.example.blue_book.data.remote.video.VideoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    @Named("backend")
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(CoreNetworkModule.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAccountApi(@Named("backend") retrofit: Retrofit): AccountApi {
        return retrofit.create(AccountApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(@Named("backend") retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFileApi(@Named("backend") retrofit: Retrofit): FileApi {
        return retrofit.create(FileApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named("backend") retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideVideoApi(@Named("backend") retrofit: Retrofit): VideoApi {
        return retrofit.create(VideoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCommentApi(@Named("backend") retrofit: Retrofit): CommentApi {
        return retrofit.create(CommentApi::class.java)
    }
}
