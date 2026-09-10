package com.example.blue_book.provider

import com.example.blue_book.data.VideoCardInfo

/**
* 视频服务接口 — 由 feature-video 模块提供
*/
interface IVideoProvider {

	suspend fun fetchRandomVideos(cursorId: Long? = null, size: Int? = 10): Result<List<VideoCardInfo>>

	/** 关注流：当前登录用户所关注作者的作品（未登录返回空） */
	suspend fun fetchFollowingFeed(cursorId: Long? = null, size: Int? = 10): Result<List<VideoCardInfo>>

	/** 本地流：按城市过滤（region 为空时后端降级为全量 feed） */
	suspend fun fetchRegionFeed(region: String, cursorId: Long? = null, size: Int? = 10): Result<List<VideoCardInfo>>

	/** 单条视频（消息中心等场景按 id 获取播放卡） */
	suspend fun fetchVideoById(aid: Long): Result<VideoCardInfo>

	suspend fun fetchVideosByKeyword(keyword: String, cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit>

	suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit>

	suspend fun fetchLikedVideos(cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	suspend fun fetchCollectedVideos(cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	suspend fun fetchUserVideos(userId: Long, cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

	/** 删除视频（仅发布者本人）：成功退出后列表端需移除对应项 */
	suspend fun deleteVideo(videoId: Long): Result<Unit>
}
