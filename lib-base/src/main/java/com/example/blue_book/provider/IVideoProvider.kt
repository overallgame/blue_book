package com.example.blue_book.provider

import com.example.blue_book.data.VideoCardInfo

/**
* 视频服务接口 — 由 feature-video 模块提供
*/
interface IVideoProvider {

	suspend fun fetchRandomVideos(cursorId: Long? = null, size: Int? = 10): Result<List<VideoCardInfo>>

	suspend fun fetchVideosByKeyword(keyword: String, cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit>

	suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit>

	suspend fun fetchLikedVideos(cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	suspend fun fetchCollectedVideos(cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	suspend fun fetchUserVideos(userId: Long, cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>
}
