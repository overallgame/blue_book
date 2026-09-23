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
	/** 最后活动时间（毫秒），用于"哪些会话已经没意义了"（判据见 [isExpired]） */
	val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 未完成会话的保留窗口，与后端 `ScheduledTasks.EXPIRED_UPLOAD_HOURS` 对齐。
 *
 * 客户端也要这一条：超过窗口的会话，服务端已经连分片一起清掉了，本地却还认为"没传完"，
 * 于是发布页会提示"继续上次未完成的上传（68%）"，用户点下去却从 0 重传。
 * 两边数值不一致也不至于出错（最多少提示一次、或提示一次作废的），只是提示会失准。
 */
const val UPLOAD_SESSION_RETENTION_HOURS = 48L

/**
 * 这条会话是否已超出保留窗口。
 *
 * [nowMillis] 由调用方给，便于测试；窗口按 [UploadSessionRecord.updatedAt] 计，
 * 也就是"48 小时没有活动"，而不是"创建后 48 小时"——上传中每传一片都会刷新它。
 */
fun UploadSessionRecord.isExpired(nowMillis: Long): Boolean =
	nowMillis - updatedAt > UPLOAD_SESSION_RETENTION_HOURS * 60 * 60 * 1000

/**
 * 会话状态。
 *
 * 除 `DONE` 外，其余取值在下次进入页面时都按"未完成"处理并对账（进程随时可能被杀）；
 * 细分取值只是让日志看得出上次停在哪一步（`PAUSED` 目前没有产品入口）。
 */
enum class UploadSessionStatus { PENDING, UPLOADING, PAUSED, COMPLETING, DONE, FAILED }
