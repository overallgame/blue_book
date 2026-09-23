package com.example.bluebook.file.dto

/**
 * 创建（或复用）上传会话。
 *
 * [chunkSize] 由客户端**提议**：只有客户端知道自己能读多大的片、走的什么网络。
 * 服务端会校验它落在 `[MIN_CHUNK_SIZE, MAX_CHUNK_SIZE]` 内并在响应里返回**生效值**，
 * 客户端必须用返回值切片——否则"客户端以为的"与"服务端记下的"会分叉，
 * 而合并是按服务端记下的分片契约做的。
 *
 * 越界是**拒绝**（400/13005）而不是钳制：客户端发请求前就得算好 `totalChunks`，
 * 它不可能预知服务端会钳成多少。
 */
data class UploadInitRequest(
    val fileName: String,
    val fileSize: Long,
    val fileMd5: String,
    val totalChunks: Int,
    /** 客户端提议的分片大小；缺省时服务端用默认值 */
    val chunkSize: Long? = null
)
