package com.example.bluebook.file.repository

import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UploadSessionRepository : JpaRepository<UploadSession, String> {
    fun findByFileMd5AndStatus(fileMd5: String, status: UploadStatus): Optional<UploadSession>
}
