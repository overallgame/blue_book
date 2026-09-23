package com.example.blue_book.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.blue_book.room.entity.UploadPartEntity
import com.example.blue_book.room.entity.UploadSessionEntity

/**
 * 本地上传状态的读写。
 *
 * 风格与 [UserDao] 一致：全部 `suspend`、不引 Flow——上传状态是"做一件事时查一下/写一下"，
 * 不是需要持续观察的响应式数据源（进度由上传器在内存里推给 UI）。
 */
@Dao
interface UploadSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: UploadSessionEntity)

    @Query("SELECT * FROM upload_session WHERE uri = :uri LIMIT 1")
    suspend fun session(uri: String): UploadSessionEntity?

    /**
     * 最近一条未完成会话（按最后活动时间倒序）。
     *
     * 传 `doneStatus` 而不是把 `'DONE'` 写进 SQL：枚举名是 Kotlin 侧的常量，
     * 让它在 SQL 里再硬编码一次，就等于多了一处改不到的地方。
     */
    @Query(
        "SELECT * FROM upload_session WHERE status != :doneStatus ORDER BY updated_at DESC LIMIT 1"
    )
    suspend fun latestUnfinishedSession(doneStatus: String): UploadSessionEntity?

    @Query("DELETE FROM upload_session WHERE uri = :uri")
    suspend fun deleteSession(uri: String)

    @Query("SELECT * FROM upload_part WHERE uri = :uri ORDER BY part_index")
    suspend fun parts(uri: String): List<UploadPartEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<UploadPartEntity>)

    @Query("DELETE FROM upload_part WHERE uri = :uri")
    suspend fun deleteParts(uri: String)

    @Query(
        "UPDATE upload_part SET status = :status, retry_count = :retryCount " +
            "WHERE uri = :uri AND part_index = :partIndex"
    )
    suspend fun updatePartStatus(uri: String, partIndex: Int, status: String, retryCount: Int)
}
