package com.example.bluebook.file.dto

/**
 * 查询某个会话当前的**权威**分片状态（`GET /api/file/upload/parts`）。
 *
 * 与 `init` 的分工是刻意的，避免两个接口做同一件事：
 * - `init` = 创建/复用会话（**有副作用**：可能新建行、可能作废旧会话）
 * - `listParts` = **只读**查询，用于"回到发布页时显示已传了多少"与恢复时的对账
 *
 * 客户端恢复流程以它为准：本地表只是缓存，服务端说没有的分片一律重传
 * （本地"传成功"不代表服务端"存住了"——请求成功但响应丢失、服务端磁盘被清，都会造成不一致）。
 */
data class UploadPartsResponse(
    val uploadId: String,
    val uploadedChunks: List<Int>,
    val chunkSize: Long,
    val totalChunks: Int,
    val fileSize: Long
)
