package com.example.blue_book.data.remote.video

import com.example.blue_book.data.remote.apiCall
import com.example.blue_book.data.remote.apiUnitCall
import com.example.blue_book.data.remote.video.dto2.FeedResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import javax.inject.Inject

class VideoRemoteDataSource @Inject constructor(
    private val api: VideoApi
) {

    suspend fun feed(cursorId: Long?, size: Int?): Result<FeedResponseDto> {
        return apiCall { api.feed(cursorId, size) }
    }

    suspend fun getVideoDto(videoId: Long): Result<Video2Dto> {
        return apiCall { api.getVideoDto(videoId) }
    }

    suspend fun likeVideo(videoId: Long, liked: Boolean): Result<Unit> {
        return apiUnitCall { api.likeVideo(videoId, liked) }
    }

    suspend fun collectVideo(videoId: Long, collected: Boolean): Result<Unit> {
        return apiUnitCall { api.collectVideo(videoId, collected) }
    }
}