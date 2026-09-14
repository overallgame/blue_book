package com.example.bluebook.video.service

import com.example.bluebook.video.entity.TranscodeStatus
import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.io.File

@Component
class TranscodeConsumer(
    private val videoRepository: VideoRepository
) {
    private val log = LoggerFactory.getLogger(TranscodeConsumer::class.java)

    @RabbitListener(queues = ["video.transcode"])
    fun handleTranscode(videoId: Long) {
        log.info("收到转码任务: videoId={}", videoId)
        // 任务由 TranscodeTaskPublisher 在 publish 事务提交后才投递，
        // 因此这里必然能读到该行，不需要再 sleep 等待事务提交
        val video = videoRepository.findById(videoId).orElse(null)
        if (video == null) {
            // 极端情况（行被删除等）：消息只能丢弃，定时兜底任务不会再找到它
            log.error("视频不存在，转码任务丢弃: videoId={}", videoId)
            return
        }
        doTranscode(video)
    }

    private fun doTranscode(video: Video) {
        video.transcodeStatus = TranscodeStatus.PROCESSING
        videoRepository.save(video)

        val storagePath = System.getenv("UPLOAD_PATH") ?: "/opt/blue-book/upload"
        val inputFile = File("$storagePath/videos/${video.originalUrl}")

        if (!inputFile.exists()) {
            log.error("原文件不存在: {}", inputFile.absolutePath)
            video.transcodeStatus = TranscodeStatus.FAILED
            videoRepository.save(video)
            return
        }

        val outputDir = "/opt/blue-book/hls"
        val videoId = video.id

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
                video.hlsUrl = "$videoId/master.m3u8"
                video.coverUrl = "$videoId/cover.jpg"
                video.transcodeStatus = TranscodeStatus.DONE
                log.info("转码完成: videoId={}, hlsUrl={}", videoId, video.hlsUrl)
            } else {
                log.error("转码失败: videoId={}, exitCode={}, output={}", videoId, exitCode, output)
                video.transcodeStatus = TranscodeStatus.FAILED
            }
        } catch (e: Exception) {
            log.error("转码异常: videoId={}", videoId, e)
            video.transcodeStatus = TranscodeStatus.FAILED
        }
        videoRepository.save(video)
    }
}
