package com.example.blue_book.provider

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.repository.VideoRepository
import com.example.blue_book.event.VideoInteractionBus

class VideoProviderImpl(
	private val repository: VideoRepository,
	private val interactionBus: VideoInteractionBus
) : IVideoProvider {

	override suspend fun fetchRandomVideos(cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchRandom(cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun fetchFollowingFeed(cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchFollowingFeed(cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun fetchRegionFeed(region: String, cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchRegionFeed(region, cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun fetchVideoById(aid: Long): Result<VideoCardInfo> {
		return repository.fetchVideoById(aid).map { it.toCardInfo() }
	}

	override suspend fun fetchVideosByKeyword(keyword: String, cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchByKeyword(keyword, cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit> {
		val result = repository.likeVideo(aid, liked)
		// 成功才广播：列表页据此同步卡片爱心/计数（失败由调用方回滚）
		if (result.isSuccess) interactionBus.publishLike(aid, liked)
		return result
	}

	override suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit> {
		val result = repository.collectVideo(aid, collected)
		if (result.isSuccess) interactionBus.publishCollect(aid, collected)
		return result
	}

	override suspend fun fetchLikedVideos(cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchLikedVideos(cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun fetchCollectedVideos(cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchCollectedVideos(cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun fetchUserVideos(userId: Long, cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchUserVideos(userId, cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun deleteVideo(videoId: Long): Result<Unit> {
		return repository.deleteVideo(videoId)
	}

	private fun Video.toCardInfo() = VideoCardInfo(
		aid = aid,
		cid = cid,
		like = like,
		image = image,
		avatar = avatar,
		collection = collection,
		nickname = nickname,
		description = description,
		playUrl = playUrl,
		isLike = isLike,
		isCollect = isCollect,
		commentCount = commentCount,
		uploaderId = uploaderId,
		isFollowed = isFollowed
	)
}
