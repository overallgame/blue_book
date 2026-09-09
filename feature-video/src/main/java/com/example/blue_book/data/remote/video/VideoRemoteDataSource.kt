package com.example.blue_book.data.remote.video

import com.example.blue_book.data.remote.video.dto2.FeedResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

class VideoRemoteDataSource @Inject constructor(
	private val apiGateway: ApiGateway
) {
	private val api = apiGateway.createApi(VideoApi::class.java)

	suspend fun feed(cursorId: Long?, size: Int?): Result<FeedResponseDto> =
		apiGateway.apiResult { api.feed(cursorId, size) }

	suspend fun feedFollowing(cursorId: Long?, size: Int?): Result<FeedResponseDto> =
		apiGateway.apiResult { api.feedFollowing(cursorId, size) }

	suspend fun searchVideos(keyword: String, cursorId: Long?, size: Int?): Result<FeedResponseDto> =
		apiGateway.apiResult { api.searchVideos(keyword, cursorId, size) }

	suspend fun getVideoDto(videoId: Long): Result<Video2Dto> =
		apiGateway.apiResult { api.getVideoDto(videoId) }

	/** 转码状态：COMPLETED / PROCESSING 等，用于播放失败时的用户提示 */
	suspend fun transcodeStatus(videoId: Long): Result<String> =
		apiGateway.apiResult { api.transcodeStatus(videoId) }

	suspend fun likeVideo(videoId: Long, liked: Boolean): Result<Unit> =
		apiGateway.apiUnitResult { api.likeVideo(videoId, liked) }

	suspend fun collectVideo(videoId: Long, collected: Boolean): Result<Unit> =
		apiGateway.apiUnitResult { api.collectVideo(videoId, collected) }

	suspend fun getPlayUrl(videoId: Long, cid: Long): Result<String> =
		apiGateway.apiResult { api.getPlayUrl(videoId, cid) }

	suspend fun myLikes(cursorId: Long?, size: Int?): Result<FeedResponseDto> =
		apiGateway.apiResult { api.myLikes(cursorId, size) }

	suspend fun myCollections(cursorId: Long?, size: Int?): Result<FeedResponseDto> =
		apiGateway.apiResult { api.myCollections(cursorId, size) }

	suspend fun userVideos(userId: Long, cursorId: Long?, size: Int?): Result<FeedResponseDto> =
		apiGateway.apiResult { api.userVideos(userId, cursorId, size) }

	suspend fun followUser(targetUserId: Long): Result<Unit> =
		apiGateway.apiUnitResult { api.followUser(targetUserId) }

	suspend fun unfollowUser(targetUserId: Long): Result<Unit> =
		apiGateway.apiUnitResult { api.unfollowUser(targetUserId) }

	/** 删除视频（含 FAILED 清理）：仅发布者本人可调用 */
	suspend fun deleteVideo(videoId: Long): Result<Unit> =
		apiGateway.apiUnitResult { api.deleteVideo(videoId) }
}
