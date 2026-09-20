package com.example.blue_book.di

import com.example.blue_book.data.repository.CommentRepositoryImpl
import com.example.blue_book.data.repository.VideoRepositoryImpl
import com.example.blue_book.domain.repository.CommentRepository
import com.example.blue_book.domain.repository.VideoRepository
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.provider.VideoProviderImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VideoRepositoryModule {

	@Binds
	@Singleton
	abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

	@Binds
	@Singleton
	abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository

	companion object {

		/** 跨模块服务：接口在 lib-base、实现在本模块，其它模块注入接口即可 */
		@Provides
		@Singleton
		fun provideVideoProvider(
			repository: VideoRepository,
			interactionBus: VideoInteractionBus
		): IVideoProvider = VideoProviderImpl(repository, interactionBus)
	}
}
