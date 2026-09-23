package com.example.blue_book.data.remote.video

import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitResponseDto
import com.example.blue_book.data.remote.video.dto2.UploadPartsResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

	/** [partMd5] 是这一片的 MD5：带了服务端就当场校验，不符返回 13006（客户端重传该片） */
	@Multipart
	@POST("/api/file/upload/chunk")
	suspend fun uploadChunk(
		@Query("uploadId") uploadId: String,
		@Query("chunkIndex") chunkIndex: Int,
		@Part file: MultipartBody.Part,
		@Query("partMd5") partMd5: String? = null
	): Response<ApiResponse<Any>>

	/**
	 * 只读查询权威分片状态。
	 *
	 * 与 init 分工不同：init 会创建/复用会话（**有副作用**），
	 * 而"进发布页看一眼上次传到哪了"不该产生任何副作用。
	 */
	@GET("/api/file/upload/parts")
	suspend fun listParts(
		@Query("uploadId") uploadId: String
	): Response<ApiResponse<UploadPartsResponseDto>>

	/** 放弃上传：立刻释放服务端分片磁盘，不必等 24 小时的过期清理 */
	@POST("/api/file/upload/abort")
	suspend fun abortUpload(
		@Query("uploadId") uploadId: String
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
