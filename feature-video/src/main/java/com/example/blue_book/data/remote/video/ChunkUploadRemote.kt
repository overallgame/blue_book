package com.example.blue_book.data.remote.video

import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitResponseDto
import com.example.blue_book.data.remote.video.dto2.UploadPartsResponseDto
import okhttp3.MultipartBody

/**
 * 分片上传需要的**远端能力**（窄接口）。
 *
 * 两点用意：
 * 1. **上传器只依赖它真正用到的 5 个方法**，而不是整个 `PublishRemoteDataSource`
 *    （那里还有 `publish`，与分片上传无关）。接口窄了，能替换的实现也就好写了。
 * 2. 让上传编排**可测**：`PublishRemoteDataSource` 依赖 `ApiGateway`（需要 Android Context、
 *    DataStore、TokenHolder），在纯 JVM 测试里造不出来。测试用一个"真 Retrofit 指向
 *    MockWebServer"的实现即可——HTTP 是真的，只是服务端换成了假的。
 *
 * 与 `feature-scan` 的 `BarcodeScanner`（接口）/ `MlKitBarcodeScanner`（实现）同一套做法。
 * 注意它暴露了 `okhttp3.MultipartBody`：这没问题，因为它只在本层被实现与使用，
 * UI 层拿到的是 `ChunkUploader`（不认识 okhttp 类型）。
 */
interface ChunkUploadRemote {

    suspend fun initUpload(body: UploadInitRequestDto): Result<UploadInitResponseDto>

    /**
     * @param partMd5 这一片的 MD5。服务端收到后当场校验，不符按 13006 拒收 → 客户端重传该片，
     *   而不是等整个文件传完才发现某一片坏了
     */
    suspend fun uploadChunk(
        uploadId: String,
        chunkIndex: Int,
        part: MultipartBody.Part,
        partMd5: String? = null
    ): Result<Unit>

    suspend fun completeUpload(uploadId: String): Result<String>

    /** 只读查询权威分片状态（恢复时对账、进页面看进度） */
    suspend fun listParts(uploadId: String): Result<UploadPartsResponseDto>

    /** 放弃上传：立刻释放服务端分片磁盘 */
    suspend fun abortUpload(uploadId: String): Result<Unit>
}
