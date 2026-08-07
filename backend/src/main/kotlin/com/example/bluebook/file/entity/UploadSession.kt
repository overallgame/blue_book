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

    @Column(name = "chunk_size")
    var chunkSize: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    var status: UploadStatus = UploadStatus.UPLOADING,

    @Column(name = "video_id")
    var videoId: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UploadStatus { UPLOADING, MERGING, DONE, EXPIRED }
