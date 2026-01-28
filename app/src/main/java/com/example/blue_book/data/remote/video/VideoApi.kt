package com.example.blue_book.data.remote.video

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.remote.video.dto2.FeedResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VideoApi {

    @GET("/api/v2/feed")
    suspend fun feed(
        @Query("cursorId") cursorId: Long? = null,
        @Query("size") size: Int? = null
    ): Response<ApiResponse<FeedResponseDto>>

    @GET("/api/v2/videos/{videoId}/dto")
    suspend fun getVideoDto(
        @Path("videoId") videoId: Long
    ): Response<ApiResponse<Video2Dto>>

    @POST("/api/v2/videos/{videoId}/like")
    suspend fun likeVideo(
        @Path("videoId") videoId: Long,
        @Query("liked") liked: Boolean
    ): Response<ApiResponse<Any>>

    @POST("/api/v2/videos/{videoId}/collect")
    suspend fun collectVideo(
        @Path("videoId") videoId: Long,
        @Query("collected") collected: Boolean
    ): Response<ApiResponse<Any>>
}