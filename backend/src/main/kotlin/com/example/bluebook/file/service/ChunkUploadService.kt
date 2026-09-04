package com.example.bluebook.file.service

import com.example.bluebook.common.BusinessException
import com.example.bluebook.common.ChunkMissingException
import com.example.bluebook.common.InvalidFileTypeException
import com.example.bluebook.file.dto.UploadInitRequest
import com.example.bluebook.file.dto.UploadInitResponse
import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import com.example.bluebook.file.repository.UploadSessionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Service
class ChunkUploadService(
    private val uploadSessionRepository: UploadSessionRepository,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${app.upload.storage-path}") private val storagePath: String,
    @Value("\${app.upload.chunk-size:2097152}") private val defaultChunkSize: Int
) {
    fun initUpload(userId: Long, request: UploadInitRequest): UploadInitResponse {
        // Check for dedup (秒传): same MD5 already uploaded
        val existing = uploadSessionRepository.findByFileMd5AndStatus(request.fileMd5, UploadStatus.DONE)
        if (existing.isPresent) {
            return UploadInitResponse(uploadId = existing.get().id, skipUpload = true)
        }

        val uploadId = UUID.randomUUID().toString()
        val session = UploadSession(
            id = uploadId, userId = userId, fileName = request.fileName,
            fileSize = request.fileSize, fileMd5 = request.fileMd5,
            totalChunks = request.totalChunks, chunkSize = request.chunkSize
        )
        uploadSessionRepository.save(session)

        // Check which chunks already exist (for resume)
        val chunkDir = File("$storagePath/chunks/$uploadId")
        val uploadedChunks = if (chunkDir.exists()) {
            chunkDir.listFiles()?.map { it.name.toIntOrNull() }?.filterNotNull() ?: emptyList()
        } else {
            emptyList()
        }
        return UploadInitResponse(uploadId = uploadId, uploadedChunks = uploadedChunks)
    }

    fun uploadChunk(uploadId: String, chunkIndex: Int, chunkData: ByteArray) {
        val session = uploadSessionRepository.findById(uploadId)
            .orElseThrow { BusinessException(13002, "上传会话不存在或已过期") }
        if (session.status != UploadStatus.UPLOADING)
            throw BusinessException(13002, "上传会话状态异常")

        val chunkDir = File("$storagePath/chunks/$uploadId")
        chunkDir.mkdirs()
        val chunkFile = File(chunkDir, chunkIndex.toString())
        chunkFile.writeBytes(chunkData)

        // Track progress in Redis
        val hashOps = redisTemplate.opsForHash<String, String>()
        hashOps.put("upload:$uploadId", "chunk_$chunkIndex", "1")
        hashOps.put("upload:$uploadId", "lastChunkTime", System.currentTimeMillis().toString())
    }

    fun getProgress(uploadId: String): List<Int> {
        val keys = redisTemplate.opsForHash<String, String>().keys("upload:$uploadId")
        return keys.filter { it.toString().startsWith("chunk_") }
            .map { it.toString().removePrefix("chunk_").toInt() }
            .sorted()
    }

    fun completeUpload(userId: Long, uploadId: String): String {
        val session = uploadSessionRepository.findById(uploadId)
            .orElseThrow { BusinessException(13002, "上传会话不存在") }

        val totalChunks = session.totalChunks ?: throw ChunkMissingException()
        val uploadedChunks = getProgress(uploadId)
        if (uploadedChunks.size != totalChunks) {
            throw ChunkMissingException()
        }

        // Merge chunks
        val ext = session.fileName?.substringAfterLast('.') ?: "mp4"
        val dir = File("$storagePath/videos/${LocalDateTime.now().toLocalDate()}")
        dir.mkdirs()
        val finalFileName = "$uploadId.$ext"
        val finalFile = File(dir, finalFileName)
        finalFile.outputStream().use { out ->
            for (i in 0 until totalChunks) {
                val chunkFile = File("$storagePath/chunks/$uploadId/$i")
                if (chunkFile.exists()) {
                    chunkFile.inputStream().use { it.copyTo(out) }
                }
            }
        }

        // Verify MD5
        val actualMd5 = computeMd5(finalFile)
        if (session.fileMd5 != null && actualMd5 != session.fileMd5) {
            finalFile.delete()
            throw BusinessException(13004, "文件校验失败，请重新上传")
        }

        // Update session
        session.status = UploadStatus.DONE
        session.updatedAt = LocalDateTime.now()
        uploadSessionRepository.save(session)

        // Clean up chunks
        File("$storagePath/chunks/$uploadId").deleteRecursively()
        redisTemplate.delete("upload:$uploadId")

        // Return relative file path
        return "${LocalDateTime.now().toLocalDate()}/$finalFileName"
    }

    private fun computeMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
