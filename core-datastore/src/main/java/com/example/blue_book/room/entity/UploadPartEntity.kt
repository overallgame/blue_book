package com.example.blue_book.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 本地分片账本（客户端侧）。
 *
 * 复合主键 `(uri, part_index)`：一片在一个会话里只能有一条记录。
 *
 * 列名刻意避开 SQL 关键字：`index` 与 `offset` 都是 SQLite 的保留字，
 * 直接拿来当列名在部分语句里要转义，改名比到处加反引号省心。
 *
 * **没有外键**：这两张表是缓存，一致性由 `UploadSessionStoreImpl` 的显式删除维护
 * （删会话时先删分片）。加 `FOREIGN KEY ... CASCADE` 会引入两个额外风险——
 * 手工 SQL 的迁移语句必须与 Room 期望的 schema 逐字对得上、且写入顺序被约束成"先会话后分片"——
 * 而这里的收益为零（只有本模块会写它）。复合主键的索引已经覆盖 `WHERE uri = ?` 的查询。
 */
@Entity(tableName = "upload_part", primaryKeys = ["uri", "part_index"])
data class UploadPartEntity(
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "part_index") val partIndex: Int,
    @ColumnInfo(name = "part_offset") val partOffset: Long,
    @ColumnInfo(name = "part_size") val partSize: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "retry_count") val retryCount: Int
)
