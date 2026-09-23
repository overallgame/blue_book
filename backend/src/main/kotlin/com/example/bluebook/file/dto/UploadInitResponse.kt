package com.example.bluebook.file.dto

/**
 * 创建/复用会话的结果。
 *
 * 一个接口同时解决三件事（秒传、断点续传、分片契约对齐），所以三个字段都不是可有可无的：
 * - [skipUpload] 秒传命中：服务端已有同 MD5 的完整文件，客户端直接调 complete
 * - [uploadedChunks] 断点续传：服务端权威的已落盘分片，客户端跳过它们
 * - [chunkSize] **生效的分片大小**：客户端必须用这个值切片，
 *   否则它算出的 offset 与服务端合并时的假设不一致
 */
data class UploadInitResponse(
    val uploadId: String,
    val skipUpload: Boolean = false,
    val uploadedChunks: List<Int> = emptyList(),
    val chunkSize: Long
)
