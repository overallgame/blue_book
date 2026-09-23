package com.example.bluebook.file.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "upload_session")
class UploadSession(
    @Id
    @Column(name = "id", nullable = false, length = 36)
    var id: String,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "file_name", length = 255)
    var fileName: String? = null,

    @Column(name = "file_size")
    var fileSize: Long? = null,

    @Column(name = "file_md5", length = 32)
    var fileMd5: String? = null,

    @Column(name = "total_chunks")
    var totalChunks: Int? = null,

    /**
     * 本次会话使用的分片大小（字节）。
     *
     * 服务端**必须记住它**，不能交给客户端每次自己决定：合并是按 index 顺序硬拼，
     * 前后分片大小不一致会拼出一个"长度对、内容错"的文件。有了这一列，续传时才能判断
     * "这次的分片契约与上次是否相同"，不同就作废重来（见 `ChunkUploadService.initUpload`）。
     */
    @Column(name = "chunk_size")
    var chunkSize: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    var status: UploadStatus = UploadStatus.UPLOADING,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UploadStatus { UPLOADING, MERGING, DONE, EXPIRED }
