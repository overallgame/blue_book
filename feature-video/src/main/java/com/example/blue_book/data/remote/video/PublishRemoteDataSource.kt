package com.example.blue_book.data.remote.video

import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

/** 发布链路数据源：分块上传 + 发布 */
class PublishRemoteDataSource @Inject constructor(
	private val apiGateway: ApiGateway
) {
	private val api = apiGateway.createApi(PublishApi::class.java)

	suspend fun initUpload(body: UploadInitRequestDto): Result<UploadInitResponseDto> =
		apiGateway.apiResult { api.initUpload(body) }

	suspend fun uploadChunk(uploadId: String, chunkIndex: Int, part: okhttp3.MultipartBody.Part): Result<Unit> =
		apiGateway.apiUnitResult { api.uploadChunk(uploadId, chunkIndex, part) }

	suspend fun completeUpload(uploadId: String): Result<String> =
		apiGateway.apiResult { api.completeUpload(uploadId) }

	suspend fun publish(body: PublishRequestDto): Result<Video2Dto> =
		apiGateway.apiResult { api.publish(body) }
}
