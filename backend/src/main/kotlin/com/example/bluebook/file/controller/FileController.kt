package com.example.bluebook.file.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.common.CommonResult
import com.example.bluebook.file.dto.UploadInitRequest
import com.example.bluebook.file.dto.UploadInitResponse
import com.example.bluebook.file.dto.UploadPartsResponse
import com.example.bluebook.file.service.ChunkUploadService
import com.example.bluebook.file.service.FileService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * 文件与分片上传。
 *
 * `/api/file/` 下的接口都不在 `SecurityConfig` 的免鉴权名单里，所以这几个分片接口都要求登录态，
 * 下面的 [currentUserId] 不会拿到 0。**即便如此服务层仍然做归属校验**：
 * "接口要求登录"只说明调用者有身份，不说明这个 uploadId 是他的。
 */
@RestController
@RequestMapping("/api/file")
class FileController(
    private val fileService: FileService,
    private val chunkUploadService: ChunkUploadService
) {
    private fun currentUserId(): Long =
        SecurityContextHolder.getContext().authentication?.principal as? Long ?: 0

    @PostMapping("/upload")
    fun uploadFile(@RequestParam("file") file: MultipartFile): CommonResult<String> {
        val path = fileService.uploadImage(file)
        return CommonResult.ok(path)
    }

    @PostMapping("/upload/image")
    fun uploadImage(@RequestParam("file") file: MultipartFile): ApiResponse<String> {
        val path = fileService.uploadImage(file)
        return ApiResponse.ok(path)
    }

    /** 创建或复用上传会话；响应里的 `chunkSize` 是**生效值**，客户端必须用它来切片 */
    @PostMapping("/upload/init")
    fun initUpload(@RequestBody request: UploadInitRequest): ApiResponse<UploadInitResponse> =
        ApiResponse.ok(chunkUploadService.initUpload(currentUserId(), request))

    /** 上传一片。同一 (uploadId, chunkIndex) 重复调用是幂等的（覆盖写） */
    @PostMapping("/upload/chunk")
    fun uploadChunk(
        @RequestParam("uploadId") uploadId: String,
        @RequestParam("chunkIndex") chunkIndex: Int,
        @RequestParam("file") file: MultipartFile
    ): ApiResponse<Any> {
        // 把 MultipartFile 直接交给服务层流式落盘，**不取 file.bytes**：
        // 客户端 3 片并发时，取字节数组意味着堆上同时压着 3×分片大小
        chunkUploadService.uploadChunk(currentUserId(), uploadId, chunkIndex, file)
        return ApiResponse.ok()
    }

    /** 只读查询权威分片状态（恢复时对账、进页面看进度） */
    @GetMapping("/upload/parts")
    fun listParts(@RequestParam uploadId: String): ApiResponse<UploadPartsResponse> =
        ApiResponse.ok(chunkUploadService.listParts(currentUserId(), uploadId))

    /** 放弃上传：立刻释放服务端磁盘，不必等 24 小时的过期清理 */
    @PostMapping("/upload/abort")
    fun abortUpload(@RequestParam uploadId: String): ApiResponse<Any> {
        chunkUploadService.abortUpload(currentUserId(), uploadId)
        return ApiResponse.ok()
    }

    @PostMapping("/upload/complete")
    fun completeUpload(@RequestParam("uploadId") uploadId: String): ApiResponse<String> {
        val path = chunkUploadService.completeUpload(currentUserId(), uploadId)
        return ApiResponse.ok(path)
    }
}
