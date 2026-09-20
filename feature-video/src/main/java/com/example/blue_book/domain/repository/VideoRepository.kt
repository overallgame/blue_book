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

	/**
	 * 转码状态（服务端返回 "DONE" / "FAILED"，其余视为处理中）。
	 *
	 * 播放失败时轮询用。此前 VideoViewModel 是直接注入 VideoRemoteDataSource 拿这个能力的
	 * ——因为本接口当时没有它，于是出现了「ViewModel 穿透到数据源」的分层穿透，
	 * 而那个数据源的构造依赖 ApiGateway（要 Android Context），把这个 ViewModel
	 * 整个拖出了纯 JVM 单测的范围。补在这里既修了分层，也让 VideoViewModel 可测。
	 */
	suspend fun transcodeStatus(videoId: Long): Result<String>

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
