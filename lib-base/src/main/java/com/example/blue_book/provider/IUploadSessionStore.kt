package com.example.blue_book.provider

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadSessionRecord

/**
 * 本地上传状态（会话 + 分片）的读写。
 *
 * 接口在 lib-base、实现在 core-datastore（与 [IUserStore] 同一套约定），
 * 消费方 `feature-video` 因此不依赖 `:core-datastore` 的编译产物。
 *
 * 全是 suspend、没有 Flow：进度由上传器在内存里推给 UI，这里只负责落库。
 */
interface IUploadSessionStore {

	/** 按内容 URI 取会话；没有（或已 DONE 被清掉）返回 null */
	suspend fun getSession(uri: String): UploadSessionRecord?

	/** 最近一条**未完成**的会话（按最后活动时间倒序）。进发布页时用它提示"继续上次上传" */
	suspend fun latestUnfinishedSession(): UploadSessionRecord?

	/** 建或更新会话（幂等） */
	suspend fun upsertSession(session: UploadSessionRecord)

	/** 整批写入某个会话的分片（对账后一次性落库，避免逐片写 N 次） */
	suspend fun replaceParts(uri: String, parts: List<UploadPartRecord>)

	suspend fun getParts(uri: String): List<UploadPartRecord>

	/** 更新单片状态（上传成功/失败时各一次） */
	suspend fun updatePartStatus(uri: String, index: Int, status: com.example.blue_book.data.UploadPartStatus, retryCount: Int)

	/**
	 * 丢掉一条会话及其分片。
	 *
	 * 两种场景都会用：上传成功（账本没用了）、用户放弃（连同服务端会话一起清）。
	 */
	suspend fun deleteSession(uri: String)
}
