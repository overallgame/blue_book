package com.example.bluebook.file.repository

import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UploadSessionRepository : JpaRepository<UploadSession, String> {
    fun findByFileMd5AndStatus(fileMd5: String, status: UploadStatus): Optional<UploadSession>

    /** 断点续传：同用户 + 同文件 MD5 + 同大小 + 未完成的上传会话（最近一次） */
    fun findFirstByUserIdAndFileMd5AndStatusAndFileSizeOrderByUpdatedAtDesc(
        userId: Long,
        fileMd5: String,
        status: UploadStatus,
        fileSize: Long
    ): Optional<UploadSession>
}
