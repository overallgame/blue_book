package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.Video

interface VideoRepository {

	suspend fun fetchRandom(cursorId: Long?, size: Int?): Result<List<Video>>

	/** 单条视频（消息中心等场景按 id 获取首条播放卡） */
	suspend fun fetchVideoById(videoId: Long): Result<Video>

	suspend fun fetchFollowingFeed(cursorId: Long?, size: Int?): Result<List<Video>>

	/** 本地流：按城市过滤（region 为空时后端降级为全量） */
	suspend fun fetchRegionFeed(region: String, cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchByKeyword(keyword: String, cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchPlayUrl(aid: Long, cid: Long): Result<String>

	suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit>

	suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit>

	suspend fun fetchLikedVideos(cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchCollectedVideos(cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchUserVideos(userId: Long, cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun followUser(targetUserId: Long): Result<Unit>

	suspend fun unfollowUser(targetUserId: Long): Result<Unit>

	suspend fun deleteVideo(videoId: Long): Result<Unit>

	suspend fun reportView(videoId: Long): Result<Unit>
}
