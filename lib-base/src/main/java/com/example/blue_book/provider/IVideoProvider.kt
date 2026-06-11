package com.example.blue_book.provider

import com.example.blue_book.data.VideoCardInfo

/**
 * 视频服务接口 — 由 feature-video 模块提供
 */
interface IVideoProvider {

    suspend fun fetchRandomVideos(cursorId: Long? = null, size: Int? = 10): Result<List<VideoCardInfo>>

    suspend fun fetchVideosByKeyword(keyword: String, cursorId: Long? = null, size: Int? = 20): Result<List<VideoCardInfo>>

    suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit>
}
