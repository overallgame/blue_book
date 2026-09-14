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
    @Value("\${app.upload.storage-path}") private val storagePath: String
) {
    fun initUpload(userId: Long, request: UploadInitRequest): UploadInitResponse {
        // 秒传：该文件此前已合并完成
        val done = uploadSessionRepository.findByFileMd5AndStatus(request.fileMd5, UploadStatus.DONE)
        if (done.isPresent) {
            return UploadInitResponse(uploadId = done.get().id, skipUpload = true)
        }

        // 断点续传：同用户、同文件且大小一致的中断会话，复用原 uploadId 与已传分片
        val pending = uploadSessionRepository
            .findFirstByUserIdAndFileMd5AndStatusAndFileSizeOrderByUpdatedAtDesc(
                userId, request.fileMd5, UploadStatus.UPLOADING, request.fileSize
            )
        if (pending.isPresent) {
            val session = pending.get()
            // 续传也算一次活动：刷新 updatedAt，否则 24 小时的过期清理会把
            // 正在续传的会话连同已传分片一起删掉
            session.updatedAt = LocalDateTime.now()
            uploadSessionRepository.save(session)
            return UploadInitResponse(
                uploadId = session.id,
                uploadedChunks = uploadedChunks(session.id)
            )
        }

        val uploadId = UUID.randomUUID().toString()
        val session = UploadSession(
            id = uploadId, userId = userId, fileName = request.fileName,
            fileSize = request.fileSize, fileMd5 = request.fileMd5,
            totalChunks = request.totalChunks
        )
        uploadSessionRepository.save(session)
        return UploadInitResponse(uploadId = uploadId)
    }

    /** 已上传分片：磁盘与 Redis 取并集（Redis 记录可能过期，以磁盘文件为准） */
    private fun uploadedChunks(uploadId: String): List<Int> {
        val fromDisk = File("$storagePath/chunks/$uploadId")
            .listFiles()?.mapNotNull { it.name.toIntOrNull() } ?: emptyList()
        val fromRedis = getProgress(uploadId)
        return (fromDisk + fromRedis).distinct().sorted()
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

        // 刷新 updatedAt：这一列被 cleanExpiredUploads 当作「最后活动时间」判断过期。
        // 不刷新会导致（a）续传超过 24 小时的会话被删掉，下一次分片报 13002；
        // （b）用户在 24 小时后重传时 uploadedChunks 为空、续传退化为全量重传。
        session.updatedAt = LocalDateTime.now()
        uploadSessionRepository.save(session)
    }

    private fun getProgress(uploadId: String): List<Int> {
        val keys = redisTemplate.opsForHash<String, String>().keys("upload:$uploadId")
        return keys.filter { it.toString().startsWith("chunk_") }
            .map { it.toString().removePrefix("chunk_").toInt() }
            .sorted()
    }

    fun completeUpload(userId: Long, uploadId: String): String {
        val session = uploadSessionRepository.findById(uploadId)
            .orElseThrow { BusinessException(13002, "上传会话不存在") }

        // 秒传命中（skipUpload）：文件此前已合并完成且保留在 videos 目录，
        // 直接按 uploadId 反查已存文件返回相对路径，避免重复上传/合并
        if (session.status == UploadStatus.DONE) {
            val ext = session.fileName?.substringAfterLast('.') ?: "mp4"
            val fileName = "${session.id}.$ext"
            val matched = File("$storagePath/videos").listFiles()
                ?.filter { it.isDirectory }
                ?.firstOrNull { dir -> File(dir, fileName).exists() }
            if (matched != null) return "${matched.name}/$fileName"
        }

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
