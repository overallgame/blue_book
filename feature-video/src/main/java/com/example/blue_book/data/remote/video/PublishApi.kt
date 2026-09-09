package com.example.blue_book.data.remote.video

import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * 发布链路 API：视频文件分块上传 + 发布
 * 端点对齐后端 FileController(/api/file) 与 VideoController(/api/v2/videos/publish)
 */
interface PublishApi {

	@POST("/api/file/upload/init")
	suspend fun initUpload(
		@Body body: UploadInitRequestDto
	): Response<ApiResponse<UploadInitResponseDto>>

	@Multipart
	@POST("/api/file/upload/chunk")
	suspend fun uploadChunk(
		@Query("uploadId") uploadId: String,
		@Query("chunkIndex") chunkIndex: Int,
		@Part file: MultipartBody.Part
	): Response<ApiResponse<Any>>

	@POST("/api/file/upload/complete")
	suspend fun completeUpload(
		@Query("uploadId") uploadId: String
	): Response<ApiResponse<String>>

	@POST("/api/v2/videos/publish")
	suspend fun publish(
		@Body body: PublishRequestDto
	): Response<ApiResponse<Video2Dto>>
}
