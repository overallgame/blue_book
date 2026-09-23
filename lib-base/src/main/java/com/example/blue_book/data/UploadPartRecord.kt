package com.example.blue_book.data

/**
 * 本地分片账本（对应 `upload_part` 表）。
 *
 * 服务端的已传分片列表只在联网时可得，这张表用于进页面时立刻画出进度。
 * 它是**缓存**：恢复时以服务端为准（见 `reconcile`），服务端没有的一律重传。
 *
 * 没有字节级进度列：分片是一次性发出去的，进度按"已完成片数"跳。
 */
data class UploadPartRecord(
	val uri: String,
	/** 第几片（从 0 开始） */
	val index: Int,
	val offset: Long,
	val size: Long,
	val status: UploadPartStatus = UploadPartStatus.PENDING,
	/** 这片试过几次（含成功那次）。用于退避与"这片是不是有问题"的观察 */
	val retryCount: Int = 0
)

/**
 * 分片状态。
 *
 * `UPLOADING` 在**进程被杀后一定是假的**（那次传输没有结论），所以对账时会被打回 `PENDING`。
 * 保留它是因为失败重试与并发上传都发生在同一次会话内，需要知道"这片正在飞"。
 */
enum class UploadPartStatus { PENDING, UPLOADING, DONE, FAILED }
