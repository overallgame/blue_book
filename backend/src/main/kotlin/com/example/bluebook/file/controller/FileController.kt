package com.example.bluebook.file.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.common.CommonResult
import com.example.bluebook.file.dto.UploadInitRequest
import com.example.bluebook.file.dto.UploadInitResponse
import com.example.bluebook.file.service.ChunkUploadService
import com.example.bluebook.file.service.FileService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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

    @PostMapping("/upload/init")
    fun initUpload(@RequestBody request: UploadInitRequest): ApiResponse<UploadInitResponse> =
        ApiResponse.ok(chunkUploadService.initUpload(currentUserId(), request))

    @PostMapping("/upload/chunk")
    fun uploadChunk(
        @RequestParam("uploadId") uploadId: String,
        @RequestParam("chunkIndex") chunkIndex: Int,
        @RequestParam("file") file: MultipartFile
    ): ApiResponse<Any> {
        chunkUploadService.uploadChunk(uploadId, chunkIndex, file.bytes)
        return ApiResponse.ok()
    }

    @GetMapping("/upload/progress")
    fun getProgress(@RequestParam("uploadId") uploadId: String): ApiResponse<List<Int>> =
        ApiResponse.ok(chunkUploadService.getProgress(uploadId))

    @PostMapping("/upload/complete")
    fun completeUpload(@RequestParam("uploadId") uploadId: String): ApiResponse<String> {
        val path = chunkUploadService.completeUpload(currentUserId(), uploadId)
        return ApiResponse.ok(path)
    }
}
