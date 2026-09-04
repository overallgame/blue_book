package com.example.blue_book.provider

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.repository.VideoRepository

class VideoProviderImpl(
	private val repository: VideoRepository
) : IVideoProvider {

	override suspend fun fetchRandomVideos(cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchRandom(cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun fetchVideosByKeyword(keyword: String, cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		return repository.fetchByKeyword(keyword, cursorId, size).map { list -> list.map { it.toCardInfo() } }
	}

	override suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit> {
		return repository.likeVideo(aid, liked)
	}

	override suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit> {
		return repository.collectVideo(aid, collected)
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
		uploaderId = uploaderId
	)
}
