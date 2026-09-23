package com.example.blue_book.data.remote.video

import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitResponseDto
import com.example.blue_book.data.remote.video.dto2.UploadPartsResponseDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

/**
 * 发布链路数据源：分块上传 + 发布。
 *
 * 它实现两个窄接口：[ChunkUploadRemote]（上传编排只依赖那 5 个方法）与 [VideoPublisher]
 * （发布页的编排只依赖 publish 一个方法）——都是为了"消费方只看到它需要的东西"，也因此可测。
 */
class PublishRemoteDataSource @Inject constructor(
	private val apiGateway: ApiGateway
) : ChunkUploadRemote, VideoPublisher {
	private val api = apiGateway.createApi(PublishApi::class.java)

	override suspend fun initUpload(body: UploadInitRequestDto): Result<UploadInitResponseDto> =
		apiGateway.apiResult { api.initUpload(body) }

	override suspend fun uploadChunk(
		uploadId: String,
		chunkIndex: Int,
		part: okhttp3.MultipartBody.Part,
		partMd5: String?
	): Result<Unit> = apiGateway.apiUnitResult { api.uploadChunk(uploadId, chunkIndex, part, partMd5) }

	override suspend fun listParts(uploadId: String): Result<UploadPartsResponseDto> =
		apiGateway.apiResult { api.listParts(uploadId) }

	override suspend fun abortUpload(uploadId: String): Result<Unit> =
		apiGateway.apiUnitResult { api.abortUpload(uploadId) }

	override suspend fun completeUpload(uploadId: String): Result<String> =
		apiGateway.apiResult { api.completeUpload(uploadId) }

	override suspend fun publish(body: PublishRequestDto): Result<Video2Dto> =
		apiGateway.apiResult { api.publish(body) }
}
