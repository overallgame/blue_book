package com.example.bluebook.file.repository

import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
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

    /**
     * 长时间未更新的未完成会话（用于清理分片目录，避免分片永久占盘）。
     * 同时覆盖 MERGING：合并过程中进程退出也会留下不会自愈的会话。
     */
    fun findByStatusInAndUpdatedAtBefore(
        statuses: Collection<UploadStatus>,
        cutoff: LocalDateTime
    ): List<UploadSession>
}
