package com.example.blue_book.data.remote.video.dto2

/** 发布视频请求体（对齐后端 PublishRequest） */
data class PublishRequestDto(
	val title: String?,
	val description: String?,
	val filePath: String,
	/** 发布时定位城市（"本地"流使用，未授权则为空） */
	val region: String? = null
)

/** 分块上传初始化请求（对齐后端 UploadInitRequest） */
data class UploadInitRequestDto(
	val fileName: String,
	val fileSize: Long,
	val fileMd5: String,
	val totalChunks: Int,
	val chunkSize: Int = 2 * 1024 * 1024
)

/** 分块上传初始化响应（对齐后端 UploadInitResponse） */
data class UploadInitResponseDto(
	val uploadId: String,
	val skipUpload: Boolean = false,
	val uploadedChunks: List<Int> = emptyList()
)
