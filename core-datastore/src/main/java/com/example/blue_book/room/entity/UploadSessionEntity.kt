package com.example.blue_book.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地上传会话（客户端侧）。
 *
 * 形状与 `lib-base/data/UploadSessionRecord` 一一对应；那一边是跨模块的领域模型，
 * 这一边是 Room 的存储形状，两者之间的转换在 `UploadSessionStoreImpl` 里做
 * （与 `UserEntity` ↔ `UserAccount` 同一套约定）。
 *
 * 主键用 `uri`：产品上一次只发一条视频，"同一个文件"在 Android 上就是同一个 content URI，
 * 所以它是天然的会话标识——不必再造一个 id，也不必判断"这是不是同一个文件"。
 */
@Entity(tableName = "upload_session")
data class UploadSessionEntity(
    @PrimaryKey @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    /** 缓存的整文件指纹：命中它就不必为了续传重读整个文件 */
    @ColumnInfo(name = "file_md5") val fileMd5: String,
    /** 缓存指纹时文件的最后修改时间；与 file_size 一起判断这条记录是否还描述同一个文件 */
    @ColumnInfo(name = "last_modified") val lastModified: Long?,
    @ColumnInfo(name = "chunk_size") val chunkSize: Long,
    @ColumnInfo(name = "total_chunks") val totalChunks: Int,
    @ColumnInfo(name = "upload_id") val uploadId: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
