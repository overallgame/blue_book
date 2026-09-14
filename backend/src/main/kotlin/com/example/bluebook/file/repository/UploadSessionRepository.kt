package com.example.bluebook.file.repository

import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
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
     * 只推进 updatedAt（最后活动时间），不做整行 merge。
     * 整行 save 有两个问题：(a) 每 2MB 一个分片都做一次 SELECT + 全行 UPDATE；
     * (b) 会把读取时的 status 快照写回，可能把已完成的会话改回 UPLOADING，
     * 之后 completeUpload 会因分片目录已删除而报 13002。
     */
    @Transactional
    @Modifying
    @Query("UPDATE UploadSession s SET s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    fun touch(id: String): Int

    /**
     * 长时间未更新的未完成会话（用于清理分片目录，避免分片永久占盘）。
     * 同时覆盖 MERGING：合并过程中进程退出也会留下不会自愈的会话。
     */
    fun findByStatusInAndUpdatedAtBefore(
        statuses: Collection<UploadStatus>,
        cutoff: LocalDateTime
    ): List<UploadSession>
}
