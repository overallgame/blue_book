package com.example.blue_book.di

import com.example.blue_book.data.device.AndroidLocationProvider
import com.example.blue_book.data.device.LocationProvider
import com.example.blue_book.data.remote.video.AndroidUploadSourceFactory
import com.example.blue_book.data.remote.video.ChunkUploadRemote
import com.example.blue_book.data.remote.video.ChunkUploader
import com.example.blue_book.data.remote.video.ChunkedUploader
import com.example.blue_book.data.remote.video.PublishRemoteDataSource
import com.example.blue_book.data.remote.video.UploadSourceFactory
import com.example.blue_book.data.remote.video.VideoPublisher
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

	/**
	 * 上传器**刻意不绑单例**（与 `feature-scan` 的 `BarcodeScanner` 同理）：
	 * 它无状态，唯一持有的东西由 `upload(source)` 传进来。
	 * 这里用 `@Binds` 是为了让发布页只认识接口——`PublishViewModel` 的取消与续传
	 * 才能用假实现单测。
	 */
	@Binds
	abstract fun bindChunkUploader(impl: ChunkedUploader): ChunkUploader

	/** 上传器只依赖窄接口（见 [ChunkUploadRemote]）——它不该拿到 `publish` 那种无关方法 */
	@Binds
	abstract fun bindChunkUploadRemote(impl: PublishRemoteDataSource): ChunkUploadRemote

	/** 发布页同理：它只该看到 `publish`，上传那 5 个方法与它无关 */
	@Binds
	abstract fun bindVideoPublisher(impl: PublishRemoteDataSource): VideoPublisher

	/**
	 * 两个平台能力，收进接口后发布页的 ViewModel 构造签名里就没有平台类型了，
	 * 于是"取消要真的停掉""publish 只调一次"这类规则可以被单测钉住。
	 */
	@Binds
	abstract fun bindUploadSourceFactory(impl: AndroidUploadSourceFactory): UploadSourceFactory

	@Binds
	abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider

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
