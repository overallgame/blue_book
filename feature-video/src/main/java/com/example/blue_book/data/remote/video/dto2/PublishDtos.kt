package com.example.blue_book.data.remote.video.dto2

/** 发布视频请求体（对齐后端 PublishRequest） */
data class PublishRequestDto(
	val title: String?,
	val description: String?,
	val filePath: String,
	/** 发布时定位城市（"本地"流使用，未授权则为空） */
	val region: String? = null
)

/**
 * 分块上传初始化请求（对齐后端 UploadInitRequest）。
 *
 * [chunkSize] 是**提议值**：服务端校验它落在允许区间内（1MB~8MB），越界会 400。
 * 客户端必须用响应里的 `chunkSize` 切片，而不是这里的值。
 */
data class UploadInitRequestDto(
	val fileName: String,
	val fileSize: Long,
	val fileMd5: String,
	val totalChunks: Int,
	val chunkSize: Long
)

/**
 * 分块上传初始化响应（对齐后端 UploadInitResponse）。
 *
 * [skipUpload] 秒传命中；[uploadedChunks] 服务端权威的已传分片（续传靠它）；
 * [chunkSize] **生效的分片大小**——客户端必须用它切片。
 */
data class UploadInitResponseDto(
	val uploadId: String,
	val skipUpload: Boolean = false,
	val uploadedChunks: List<Int> = emptyList(),
	val chunkSize: Long
)

/** 只读查询会话当前状态（对齐后端 UploadPartsResponse）：恢复时对账、进页面看进度 */
data class UploadPartsResponseDto(
	val uploadId: String,
	val uploadedChunks: List<Int> = emptyList(),
	val chunkSize: Long,
	val totalChunks: Int,
	val fileSize: Long
)
