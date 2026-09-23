package com.example.blue_book.data

/**
 * 本地上传会话（对应 `upload_session` 表）。
 *
 * 服务端也能回答"传到哪了"，这张表解决的是客户端自己需要而服务端不知道的三件事：
 * 缓存 [fileMd5] 免得续传时重读整个文件、记住"有一条没传完"（uploadId 只在进程内）、
 * 进页面时不等网络就能把进度画出来。
 *
 * [uri] 做主键：同一个文件在 Android 上就是同一个 content URI。
 */
data class UploadSessionRecord(
	/** 内容 URI（`content://...`），主键 */
	val uri: String,
	val fileName: String,
	val fileSize: Long,
	/** 缓存的整文件指纹（MD5）：命中它就不必重读整个文件 */
	val fileMd5: String,
	/**
	 * 缓存指纹时"这个文件"的最后修改时间（来源同 `UploadSource.lastModified`）。
	 *
	 * 与 [fileSize] 一起构成"这条记录还描述同一个文件吗"的判据；null 表示拿不到，
	 * 那时**不信缓存**（宁可重读一遍文件）。
	 */
	val lastModified: Long? = null,
	/** 本次会话约定的分片大小（来自服务端响应）。与当前常量不符时整条会话作废 */
	val chunkSize: Long,
	val totalChunks: Int,
	/** 服务端会话 id；init 之前为 null */
	val uploadId: String? = null,
	val status: UploadSessionStatus = UploadSessionStatus.UPLOADING,
	/** 最后活动时间（毫秒），用于"哪些会话已经没意义了" */
	val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 会话状态。
 *
 * 除 `DONE` 外，其余取值在下次进入页面时都按"未完成"处理并对账（进程随时可能被杀）；
 * 细分取值只是让日志看得出上次停在哪一步（`PAUSED` 目前没有产品入口）。
 */
enum class UploadSessionStatus { PENDING, UPLOADING, PAUSED, COMPLETING, DONE, FAILED }
