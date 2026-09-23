package com.example.blue_book.room.provider

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.UploadSessionRecord
import com.example.blue_book.data.UploadSessionStatus
import com.example.blue_book.provider.IUploadSessionStore
import com.example.blue_book.room.dao.UploadSessionDao
import com.example.blue_book.room.entity.UploadPartEntity
import com.example.blue_book.room.entity.UploadSessionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [IUploadSessionStore] 的 Room 实现（与 `UserStoreProviderImpl` 同一套写法：
 * 领域模型 ↔ 实体 的转换只在实现里发生，上层看不到 Room）。
 */
@Singleton
class UploadSessionStoreImpl @Inject constructor(
    private val dao: UploadSessionDao
) : IUploadSessionStore {

    override suspend fun getSession(uri: String): UploadSessionRecord? =
        dao.session(uri)?.toRecord()

    override suspend fun latestUnfinishedSession(): UploadSessionRecord? =
        dao.latestUnfinishedSession(UploadSessionStatus.DONE.name)?.toRecord()

    override suspend fun upsertSession(session: UploadSessionRecord) {
        dao.upsertSession(session.toEntity())
    }

    /**
     * 先删后写，而不是逐片 upsert：对账结果可能比上一次**少**几条（服务端判定某些片不存在），
     * 逐片写会留下幽灵行。
     *
     * 两步之间没有事务也不需要：账本是可重建的缓存，中途崩溃只会让它缺若干行，
     * 而那些行会被下一次对账补回来（或按"待传"处理）。
     */
    override suspend fun replaceParts(uri: String, parts: List<UploadPartRecord>) {
        dao.deleteParts(uri)
        if (parts.isNotEmpty()) dao.insertParts(parts.map { it.toEntity() })
    }

    override suspend fun getParts(uri: String): List<UploadPartRecord> =
        dao.parts(uri).map { it.toRecord() }

    override suspend fun updatePartStatus(
        uri: String,
        index: Int,
        status: UploadPartStatus,
        retryCount: Int
    ) {
        dao.updatePartStatus(uri, index, status.name, retryCount)
    }

    override suspend fun deleteSession(uri: String) {
        // 先删子行：没有外键级联，顺序就由这里负责
        dao.deleteParts(uri)
        dao.deleteSession(uri)
    }
}

// ───────────────────────── 映射：只在实现里出现 ─────────────────────────

private fun UploadSessionEntity.toRecord() = UploadSessionRecord(
    uri = uri,
    fileName = fileName,
    fileSize = fileSize,
    fileMd5 = fileMd5,
    chunkSize = chunkSize,
    totalChunks = totalChunks,
    uploadId = uploadId,
    status = runCatching { UploadSessionStatus.valueOf(status) }
        .getOrDefault(UploadSessionStatus.UPLOADING),
    updatedAt = updatedAt
)

private fun UploadSessionRecord.toEntity() = UploadSessionEntity(
    uri = uri,
    fileName = fileName,
    fileSize = fileSize,
    fileMd5 = fileMd5,
    chunkSize = chunkSize,
    totalChunks = totalChunks,
    uploadId = uploadId,
    status = status.name,
    updatedAt = updatedAt
)

private fun UploadPartEntity.toRecord() = UploadPartRecord(
    uri = uri,
    index = partIndex,
    offset = partOffset,
    size = partSize,
    status = runCatching { UploadPartStatus.valueOf(status) }
        .getOrDefault(UploadPartStatus.PENDING),
    retryCount = retryCount
)

private fun UploadPartRecord.toEntity() = UploadPartEntity(
    uri = uri,
    partIndex = index,
    partOffset = offset,
    partSize = size,
    status = status.name,
    retryCount = retryCount
)
