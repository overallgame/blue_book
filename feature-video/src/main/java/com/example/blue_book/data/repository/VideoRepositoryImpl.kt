package com.example.blue_book.data.repository

import com.example.blue_book.data.mapper.toDomainVideos
import com.example.blue_book.data.remote.video.VideoRemoteDataSource
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
	private val remote: VideoRemoteDataSource
): VideoRepository {

	override suspend fun fetchRandom(cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.feed(cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun fetchVideoById(videoId: Long): Result<Video> {
		val result = remote.getVideoDto(videoId)
		return result.map { listOf(it).toDomainVideos().first() }
	}

	override suspend fun fetchFollowingFeed(cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.feedFollowing(cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun fetchRegionFeed(region: String, cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.feedRegion(region, cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun fetchByKeyword(keyword: String, cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.searchVideos(keyword, cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun fetchPlayUrl(aid: Long, cid: Long): Result<String> {
		return remote.getPlayUrl(aid, cid)
	}

	/** 直接透传：服务端返回的就是 "DONE"/"FAILED" 这类状态串，没有 DTO 需要映射 */
	override suspend fun transcodeStatus(videoId: Long): Result<String> {
		return remote.transcodeStatus(videoId)
	}

	override suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit> {
		return remote.likeVideo(aid, liked)
	}

	override suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit> {
		return remote.collectVideo(aid, collected)
	}

	override suspend fun fetchLikedVideos(cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.myLikes(cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun fetchCollectedVideos(cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.myCollections(cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun fetchUserVideos(userId: Long, cursorId: Long?, size: Int?): Result<List<Video>> {
		val result = remote.userVideos(userId, cursorId, size)
		return result.map { it.items.toDomainVideos() }
	}

	override suspend fun followUser(targetUserId: Long): Result<Unit> =
		remote.followUser(targetUserId)

	override suspend fun unfollowUser(targetUserId: Long): Result<Unit> =
		remote.unfollowUser(targetUserId)

	override suspend fun deleteVideo(videoId: Long): Result<Unit> =
		remote.deleteVideo(videoId)

	override suspend fun reportView(videoId: Long): Result<Unit> =
		remote.reportView(videoId)
}