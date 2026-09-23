package com.example.bluebook.file.service

import com.example.bluebook.common.BusinessException
import com.example.bluebook.common.ChunkMissingException
import com.example.bluebook.common.FileTooLargeException
import com.example.bluebook.common.ForbiddenException
import com.example.bluebook.common.InvalidUploadParamsException
import com.example.bluebook.file.dto.UploadInitRequest
import com.example.bluebook.file.dto.UploadInitResponse
import com.example.bluebook.file.dto.UploadPartsResponse
import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import com.example.bluebook.file.repository.UploadSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

/**
 * 分片上传的服务端。
 *
 * ## 分片状态以**磁盘**为唯一真相
 *
 * 一片在不在，由 `chunks/{uploadId}` 目录里的文件决定，不引入第二份记录：
 * 两份判据一旦分叉（"记录说齐了、磁盘其实缺一片"），就会静默拼出一个残缺文件。
 * 已传分片、完整性、合并三处判定都从这一个来源读。
 *
 * ## 并发约束
 *
 * 客户端按 3 片并发上传，所以：
 * 1. 会话行只在 init/续传时 `touch`——过期是 24 小时、一次上传是分钟级，
 *    init 时的 touch 已把会话保鲜得足够久，不必每片都 UPDATE（同一行连续 UPDATE 会争行锁）
 * 2. 分片流式落盘，不整片读进堆（3 片并发下 `file.bytes` 会让堆上是 3×分片大小）
 * 3. 分片序号必须落在 `[0, totalChunks)`：越界的分片永远不会进入合并，只会占盘
 */
@Service
class ChunkUploadService(
    private val uploadSessionRepository: UploadSessionRepository,
    @Value("\${app.upload.storage-path}") private val storagePath: String,
    @Value("\${app.upload.video-max-size}") private val videoMaxSize: Long
) {
    private val log = LoggerFactory.getLogger(ChunkUploadService::class.java)

    companion object {
        const val DEFAULT_CHUNK_SIZE = 2L * 1024 * 1024

        /** 下限：再小的话，分片数与请求数的增长换不来任何好处 */
        const val MIN_CHUNK_SIZE = 1L * 1024 * 1024

        /**
         * 上限：必须低于 `spring.servlet.multipart.max-file-size`（10MB），
         * 否则客户端按这个大小发的片会被 multipart 解析器直接拒掉（表现为 400/500 而不是业务错误）。
         * 留 2MB 余量给 multipart 的边界与头部。
         */
        const val MAX_CHUNK_SIZE = 8L * 1024 * 1024
    }

    // ───────────────────────── init：秒传 + 续传 + 分片契约对齐 ─────────────────────────

    fun initUpload(userId: Long, request: UploadInitRequest): UploadInitResponse {
        val chunkSize = request.chunkSize ?: DEFAULT_CHUNK_SIZE
        validateInitRequest(request, chunkSize)

        // 秒传：该 MD5 的文件已合并完成。内容相同即文件相同，与分片大小无关
        val done = uploadSessionRepository.findByFileMd5AndStatus(request.fileMd5, UploadStatus.DONE)
        if (done.isPresent) {
            return UploadInitResponse(
                uploadId = done.get().id,
                skipUpload = true,
                chunkSize = chunkSize
            )
        }

        // 断点续传：同用户、同文件（MD5 + 大小）的中断会话
        val pending = uploadSessionRepository
            .findFirstByUserIdAndFileMd5AndStatusAndFileSizeOrderByUpdatedAtDesc(
                userId, request.fileMd5, UploadStatus.UPLOADING, request.fileSize
            )
        if (pending.isPresent) {
            val session = pending.get()
            if (session.chunkSize == chunkSize && session.totalChunks == request.totalChunks) {
                // 续传也算一次活动：推进 updatedAt，否则 24 小时的过期清理会把
                // 正在续传的会话连同已传分片一起删掉
                uploadSessionRepository.touch(session.id)
                return UploadInitResponse(
                    uploadId = session.id,
                    uploadedChunks = uploadedChunks(session.id),
                    chunkSize = chunkSize
                )
            }

            // 分片契约变了（客户端改了分片大小）：旧分片是按旧大小落盘的，与新分片混拼必然
            // 拼错，所以作废重来——继续复用会在 complete 时报"分片缺失"，
            // 而那个文案指向"你少传了片"，与真正的原因（契约变了）无关
            log.warn(
                "上传会话 {} 的分片契约已变（旧 chunkSize={} totalChunks={}；新 chunkSize={} totalChunks={}），作废重建",
                session.id, session.chunkSize, session.totalChunks, chunkSize, request.totalChunks
            )
            purge(session.id)
        }

        val uploadId = UUID.randomUUID().toString()
        uploadSessionRepository.save(
            UploadSession(
                id = uploadId, userId = userId, fileName = request.fileName,
                fileSize = request.fileSize, fileMd5 = request.fileMd5,
                totalChunks = request.totalChunks, chunkSize = chunkSize
            )
        )
        return UploadInitResponse(uploadId = uploadId, chunkSize = chunkSize)
    }

    /**
     * 请求自洽性校验：分片大小区间、文件大小、分片数与文件大小是否对得上。越早拒绝越省一次白传。
     *
     * 分片大小越界是**拒绝**而不是钳制成合法值：客户端在发请求前就得算好 `totalChunks`，
     * 它无从知道服务端会钳成多少，钳制等于死代码。拒绝时把允许区间写进消息。
     */
    private fun validateInitRequest(request: UploadInitRequest, chunkSize: Long) {
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw InvalidUploadParamsException(
                "分片大小 $chunkSize 超出允许范围（$MIN_CHUNK_SIZE–$MAX_CHUNK_SIZE 字节）"
            )
        }
        if (request.fileSize <= 0) {
            throw InvalidUploadParamsException("文件大小非法：${request.fileSize}")
        }
        if (request.fileSize > videoMaxSize) {
            throw FileTooLargeException()
        }
        val expectedChunks = (request.fileSize + chunkSize - 1) / chunkSize
        if (request.totalChunks.toLong() != expectedChunks) {
            throw InvalidUploadParamsException(
                "分片数与分片大小不一致：文件 ${request.fileSize} 字节按 ${chunkSize} 字节切片应为 " +
                    "$expectedChunks 片，客户端给了 ${request.totalChunks} 片"
            )
        }
    }

    // ───────────────────────── 分片上传 ─────────────────────────

    fun uploadChunk(userId: Long, uploadId: String, chunkIndex: Int, chunk: MultipartFile) {
        val session = requireOwnedSession(userId, uploadId)
        if (session.status != UploadStatus.UPLOADING) {
            throw BusinessException(13002, "上传会话状态异常")
        }

        val totalChunks = session.totalChunks ?: throw ChunkMissingException()
        val chunkSize = session.chunkSize ?: DEFAULT_CHUNK_SIZE

        // 序号越界的分片永远不会进入合并，却会一直占盘
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw InvalidUploadParamsException(
                "分片序号越界：$chunkIndex（本次上传共 $totalChunks 片，序号应为 0…${totalChunks - 1}）"
            )
        }
        if (chunk.isEmpty) throw InvalidUploadParamsException("分片 $chunkIndex 内容为空")
        if (chunk.size > chunkSize) {
            throw InvalidUploadParamsException(
                "分片 $chunkIndex 过大：${chunk.size} 字节，本次会话约定的分片大小为 $chunkSize 字节"
            )
        }

        val chunkDir = File("$storagePath/chunks/$uploadId")
        chunkDir.mkdirs()
        val chunkFile = File(chunkDir, chunkIndex.toString())
        // 流式落盘而不是 file.bytes：3 片并发时后者会让堆占用变成 3×分片大小。
        // 同一 (uploadId, index) 重复上传直接覆盖——这就是分片级幂等，
        // 客户端重试时不必区分"第一次"与"重传"
        chunk.inputStream.use { input ->
            chunkFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    // ───────────────────────── 只读查询与放弃 ─────────────────────────

    /**
     * 只读查询：不动会话、不推进时间戳，所以可以放心地被"进页面看一眼进度"调用。
     *
     * 与 `init` 的分工是刻意的：`init` 有副作用（可能新建会话、可能作废旧会话），
     * 而"看一眼进度"不该产生任何副作用。
     */
    fun listParts(userId: Long, uploadId: String): UploadPartsResponse {
        val session = requireOwnedSession(userId, uploadId)
        return UploadPartsResponse(
            uploadId = session.id,
            uploadedChunks = uploadedChunks(session.id),
            chunkSize = session.chunkSize ?: DEFAULT_CHUNK_SIZE,
            totalChunks = session.totalChunks ?: 0,
            fileSize = session.fileSize ?: 0
        )
    }

    /** 放弃上传：立刻释放服务端磁盘，不必等 24 小时的过期清理 */
    fun abortUpload(userId: Long, uploadId: String) {
        val session = requireOwnedSession(userId, uploadId)
        if (session.status == UploadStatus.DONE) {
            // 已完成的会话（及其合并产物）不属于"上传中"，删它就是删用户已发布的视频
            throw ForbiddenException()
        }
        purge(uploadId)
    }

    // ───────────────────────── 合并 ─────────────────────────

    fun completeUpload(userId: Long, uploadId: String): String {
        val session = requireOwnedSession(userId, uploadId)

        // 秒传命中：合并产物已在 videos 目录，按 uploadId 反查已存文件返回相对路径，
        // 不必重复上传与合并
        if (session.status == UploadStatus.DONE) {
            val ext = session.fileName?.substringAfterLast('.') ?: "mp4"
            val fileName = "${session.id}.$ext"
            val matched = File("$storagePath/videos").listFiles()
                ?.filter { it.isDirectory }
                ?.firstOrNull { dir -> File(dir, fileName).exists() }
            if (matched != null) return "${matched.name}/$fileName"
        }

        val totalChunks = session.totalChunks ?: throw ChunkMissingException()
        val present = uploadedChunks(uploadId).toSet()
        // 缺哪几片要写日志：用户只需要看到"重传"，而排查的人需要知道是哪些片
        // （与 GlobalExceptionHandler 里"服务端异常也要留日志"同一条理由）
        val missing = (0 until totalChunks).filter { it !in present }
        if (missing.isNotEmpty()) {
            log.warn("上传会话 {} 缺 {} 片：{}", uploadId, missing.size, missing.take(20))
            throw ChunkMissingException()
        }

        val ext = session.fileName?.substringAfterLast('.') ?: "mp4"
        val dir = File("$storagePath/videos/${LocalDateTime.now().toLocalDate()}")
        dir.mkdirs()
        val finalFileName = "$uploadId.$ext"
        val finalFile = File(dir, finalFileName)
        finalFile.outputStream().use { out ->
            for (i in 0 until totalChunks) {
                val chunkFile = File("$storagePath/chunks/$uploadId/$i")
                // 上面刚确认过一片不缺，所以这里**不能静默跳过**：
                // 跳过会把"判据说齐了、磁盘其实缺文件"变成一个长度合理但内容残缺的文件
                if (!chunkFile.exists()) {
                    log.error("上传会话 {} 的分片 {} 在判据里存在、磁盘上却不存在", uploadId, i)
                    finalFile.delete()
                    throw ChunkMissingException()
                }
                chunkFile.inputStream().use { it.copyTo(out) }
            }
        }

        // Verify MD5：最后一道，也是唯一能发现"拼出来的文件不是用户选的那个"的检查
        val actualMd5 = computeMd5(finalFile)
        if (session.fileMd5 != null && actualMd5 != session.fileMd5) {
            finalFile.delete()
            throw BusinessException(13004, "文件校验失败，请重新上传")
        }

        session.status = UploadStatus.DONE
        session.updatedAt = LocalDateTime.now()
        uploadSessionRepository.save(session)
        File("$storagePath/chunks/$uploadId").deleteRecursively()

        return "${LocalDateTime.now().toLocalDate()}/$finalFileName"
    }

    // ───────────────────────── 内部 ─────────────────────────

    /**
     * 取会话并校验归属。
     *
     * 每个分片接口都要经过它：知道 uploadId 不等于有权限操作那个会话，
     * 而 uploadId 是 UUID"不易猜"不能当权限模型。不属于自己的一律 403。
     */
    private fun requireOwnedSession(userId: Long, uploadId: String): UploadSession {
        val session = uploadSessionRepository.findById(uploadId)
            .orElseThrow { BusinessException(13002, "上传会话不存在或已过期") }
        if (session.userId != userId) {
            log.warn("用户 {} 试图操作不属于自己的上传会话 {}", userId, uploadId)
            throw ForbiddenException()
        }
        return session
    }

    /** 已上传分片：**以磁盘为准**（见类注释：不引入第二份记录） */
    private fun uploadedChunks(uploadId: String): List<Int> =
        File("$storagePath/chunks/$uploadId")
            .listFiles()
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.sorted()
            ?: emptyList()

    /** 作废一个会话：删分片目录 + 删行。合并产物（videos/）不动 */
    private fun purge(uploadId: String) {
        File("$storagePath/chunks/$uploadId").deleteRecursively()
        uploadSessionRepository.findById(uploadId).ifPresent { uploadSessionRepository.delete(it) }
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
