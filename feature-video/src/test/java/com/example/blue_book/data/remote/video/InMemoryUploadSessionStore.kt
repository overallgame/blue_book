package com.example.blue_book.data.remote.video

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.UploadSessionRecord
import com.example.blue_book.provider.IUploadSessionStore

/**
 * 内存版 [IUploadSessionStore]。
 *
 * **是真实现而不是 mock**：上传器对账本的用法（先写会话、再整批写对账结果、每片更新、
 * 成功或不可重试失败时清掉）本身就是要验证的行为，用 mock 的 `verify` 只能验"调用过"，
 * 而这里能直接断言"账本最后长什么样"。
 *
 * 也不做成"每个方法都抛异常的替身"：这个接口的每个方法在上传流程里都会被走到，
 * 抛异常只会让所有用例都红。
 */
class InMemoryUploadSessionStore : IUploadSessionStore {

	private val sessions = mutableMapOf<String, UploadSessionRecord>()
	private val parts = mutableMapOf<String, List<UploadPartRecord>>()

	/** 记录发生过多少次"整批写账本"，用来断言对账真的落库了 */
	var replacePartsCalls: Int = 0
		private set

	var deleteSessionCalls: Int = 0
		private set

	override suspend fun getSession(uri: String): UploadSessionRecord? = sessions[uri]

	override suspend fun latestUnfinishedSession(): UploadSessionRecord? =
		sessions.values.maxByOrNull { it.updatedAt }

	override suspend fun upsertSession(session: UploadSessionRecord) {
		sessions[session.uri] = session
	}

	override suspend fun replaceParts(uri: String, parts: List<UploadPartRecord>) {
		replacePartsCalls++
		this.parts[uri] = parts
	}

	override suspend fun getParts(uri: String): List<UploadPartRecord> = parts[uri].orEmpty()

	override suspend fun updatePartStatus(
		uri: String,
		index: Int,
		status: UploadPartStatus,
		retryCount: Int
	) {
		parts[uri] = parts[uri].orEmpty().map { part ->
			if (part.index == index) part.copy(status = status, retryCount = retryCount) else part
		}
	}

	override suspend fun deleteSession(uri: String) {
		deleteSessionCalls++
		sessions.remove(uri)
		parts.remove(uri)
	}

	// 测试用的直通口：预置状态、检查结果
	fun seed(session: UploadSessionRecord, ledger: List<UploadPartRecord> = emptyList()) {
		sessions[session.uri] = session
		if (ledger.isNotEmpty()) parts[session.uri] = ledger
	}

	fun partsOf(uri: String): List<UploadPartRecord> = parts[uri].orEmpty()
}
