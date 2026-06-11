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

    suspend fun searchVideos(keyword: String, cursorId: Long?, size: Int?): Result<FeedResponseDto> =
        apiGateway.apiResult { api.searchVideos(keyword, cursorId, size) }

    suspend fun getVideoDto(videoId: Long): Result<Video2Dto> =
        apiGateway.apiResult { api.getVideoDto(videoId) }

    suspend fun likeVideo(videoId: Long, liked: Boolean): Result<Unit> =
        apiGateway.apiUnitResult { api.likeVideo(videoId, liked) }

    suspend fun collectVideo(videoId: Long, collected: Boolean): Result<Unit> =
        apiGateway.apiUnitResult { api.collectVideo(videoId, collected) }

    suspend fun getPlayUrl(videoId: Long, cid: Long): Result<String> =
        apiGateway.apiResult { api.getPlayUrl(videoId, cid) }
}
