package com.example.blue_book.di

import com.example.blue_book.data.remote.account.AccountApi
import com.example.blue_book.data.remote.auth.AuthApi
import com.example.blue_book.data.remote.file.FileApi
import com.example.blue_book.data.remote.user.UserApi
import com.example.blue_book.data.remote.video.VideoApi
import com.example.blue_book.core.network.TokenAuthenticator
import com.example.blue_book.core.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val BASE_URL = "http://10.0.2.2:8085/"

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(loggingInterceptor)
            .build()
    }

     @Provides
     @Singleton
     @Named("refresh")
     fun provideRefreshOkHttpClient(
         loggingInterceptor: HttpLoggingInterceptor
     ): OkHttpClient {
         return OkHttpClient.Builder()
             .connectTimeout(10, TimeUnit.SECONDS)
             .readTimeout(10, TimeUnit.SECONDS)
             .writeTimeout(10, TimeUnit.SECONDS)
             .retryOnConnectionFailure(true)
             .addInterceptor(loggingInterceptor)
             .build()
     }

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
            .baseUrl(BASE_URL)
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
}
