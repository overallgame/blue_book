package com.example.bluebook.video.service

import com.example.bluebook.video.entity.TranscodeStatus
import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.io.File

/**
 * 转码消费者。
 *
 * 两点关键约定：
 * 1. 任务由 `TranscodeTaskPublisher` 在发布事务**提交后**投递，因此这里必然能读到该行，
 *    不需要再 sleep 等事务提交。
 * 2. 转码期间的每一次状态写入都用**定向 UPDATE**，不用 `save(entity)`：实体是在 ffmpeg
 *    开始前读出的，而 `Video` 没有 `@DynamicUpdate`，`save` 会把整行快照写回，
 *    抹掉转码期间用户产生的点赞/评论/播放计数（PENDING 视频是可以被点赞和观看的）。
 */
@Component
class TranscodeConsumer(
    private val videoRepository: VideoRepository,
    private val registry: TranscodeRegistry
) {
    private val log = LoggerFactory.getLogger(TranscodeConsumer::class.java)

    @RabbitListener(queues = ["video.transcode"])
    fun handleTranscode(videoId: Long) {
        log.info("收到转码任务: videoId={}", videoId)
        val video = videoRepository.findById(videoId).orElse(null)
        if (video == null) {
            // 极端情况（行被删除等）：消息只能丢弃，定时兜底也不会再找到它
            log.error("视频不存在，转码任务丢弃: videoId={}", videoId)
            return
        }
        if (video.transcodeStatus == TranscodeStatus.DONE) {
            log.info("该视频已转码完成，跳过重复任务: videoId={}", videoId)
            return
        }
        // 标记为运行中：兜底任务据此区分「正在跑」与「已被杀」
        registry.markRunning(videoId)
        try {
            doTranscode(videoId, video.originalUrl)
        } finally {
            registry.markFinished(videoId)
        }
    }

    private fun doTranscode(videoId: Long, originalUrl: String?) {
        videoRepository.updateTranscodeStatus(videoId, TranscodeStatus.PROCESSING)

        val storagePath = System.getenv("UPLOAD_PATH") ?: "/opt/blue-book/upload"
        val inputFile = File("$storagePath/videos/$originalUrl")

        if (!inputFile.exists()) {
            log.error("原文件不存在: {}", inputFile.absolutePath)
            videoRepository.updateTranscodeStatus(videoId, TranscodeStatus.FAILED)
            return
        }

        val outputDir = "/opt/blue-book/hls"

        try {
            val script = arrayOf(
                "bash",
                "/opt/blue-book/transcode-worker.sh",
                inputFile.absolutePath,
                outputDir,
                videoId.toString()
            )
            val process = ProcessBuilder(*script)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                videoRepository.updateTranscodeDone(
                    videoId, TranscodeStatus.DONE,
                    hlsUrl = "$videoId/master.m3u8",
                    coverUrl = "$videoId/cover.jpg"
                )
                log.info("转码完成: videoId={}, hlsUrl={}/master.m3u8", videoId, videoId)
            } else {
                log.error("转码失败: videoId={}, exitCode={}, output={}", videoId, exitCode, output)
                videoRepository.updateTranscodeStatus(videoId, TranscodeStatus.FAILED)
            }
        } catch (e: Exception) {
            log.error("转码异常: videoId={}", videoId, e)
            videoRepository.updateTranscodeStatus(videoId, TranscodeStatus.FAILED)
        }
    }
}
