package com.example.blue_book.data.remote.video

import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto

/**
 * 提交发布元数据（视频已经上传完、拿到 filePath 之后的最后一步）。
 *
 * 又抽一个窄接口的理由与 [ChunkUploadRemote] 相同——**让发布页的编排可测**。
 * 这里尤其值得，因为"`publish` 只调一次、绝不重试"这条规则必须能被断言才守得住：
 * 它每次调用都会新插一行视频，响应丢失时重试会变成同一条视频两条记录。
 */
interface VideoPublisher {

    suspend fun publish(body: PublishRequestDto): Result<Video2Dto>
}
