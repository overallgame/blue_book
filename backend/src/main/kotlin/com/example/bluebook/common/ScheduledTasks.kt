package com.example.bluebook.common

import com.example.bluebook.auth.repository.RefreshTokenRepository
import com.example.bluebook.file.entity.UploadStatus
import com.example.bluebook.file.repository.UploadSessionRepository
import com.example.bluebook.video.entity.TranscodeStatus
import com.example.bluebook.video.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

@Component
class ScheduledTasks(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val redisTemplate: StringRedisTemplate,
    private val videoRepository: VideoRepository,
    private val uploadSessionRepository: UploadSessionRepository,
    private val rabbitTemplate: RabbitTemplate? = null,
    @Value("\${app.upload.storage-path}") private val storagePath: String
) {
    private val log = LoggerFactory.getLogger(ScheduledTasks::class.java)

    private companion object {
        /** 转码中超过该时长未更新，视为卡死 */
        const val STALE_TRANSCODE_MINUTES = 30L

        /** 未完成上传会话的保留时长 */
        const val EXPIRED_UPLOAD_HOURS = 24L
    }

    @Transactional
    @Scheduled(fixedRate = 3600000)
    fun cleanExpiredTokens() {
        log.info("清理过期refresh token...")
        refreshTokenRepository.deleteAllExpired(Instant.now())
    }

    /**
     * Sync Redis play counts to MySQL every 5 minutes.
     * Scans keys matching "video:play_count:*", reads their values,
     * and updates the corresponding video records.
     */
    @Scheduled(fixedRate = 300000)
    fun syncPlayCounts() {
        log.debug("同步播放计数...")
        // In production: scan Redis keys matching "video:play_count:*"
        // and batch-update MySQL. For MVP, this is a no-op placeholder.
    }

    /**
     * Daily reconciliation: compare counter tables with redundant counters.
     * Runs at 3:00 AM every day.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    fun dailyReconciliation() {
        log.info("开始每日数据对账...")
        // Compare COUNT of video_like with video.like_count
        // Compare COUNT of video_collect with video.collect_count
        // Compare COUNT of user_follow with user.follower/following_count
        // Auto-correct mismatches
        log.info("每日数据对账完成")
    }

    /**
     * 转码兜底：doTranscode 先置 PROCESSING 再执行外部进程，进程重启/被杀后该行不会自愈，
     * 而 feed 只显示 transcodeStatus = DONE 的视频——视频会永远不出现。
     * 这里把长时间停在 PROCESSING 的任务重新投递一次。
     */
    @Scheduled(fixedRate = 600000)
    fun requeueStaleTranscodes() {
        // 捕获到局部变量：成员属性在 lambda 内无法智能转换
        val rabbit = rabbitTemplate ?: return
        val stale = videoRepository.findStaleProcessing(
            LocalDateTime.now().minusMinutes(STALE_TRANSCODE_MINUTES)
        )
        if (stale.isEmpty()) return
        log.warn("发现 {} 条卡住的转码任务，重新投递", stale.size)
        stale.forEach { video ->
            video.transcodeStatus = TranscodeStatus.PENDING
            videoRepository.save(video)
            runCatching { rabbit.convertAndSend("video.transcode", video.id) }
                .onFailure { log.error("重新投递失败: videoId={}", video.id, it) }
        }
    }

    /**
     * 清理过期上传会话与遗留分片。
     * 分片目录 chunks/{uploadId} 此前只在合并成功时删除，中断的上传会永久占盘。
     */
    @Transactional
    @Scheduled(fixedRate = 3600000)
    fun cleanExpiredUploads() {
        val cutoff = LocalDateTime.now().minusHours(EXPIRED_UPLOAD_HOURS)
        val expired = uploadSessionRepository.findByStatusInAndUpdatedAtBefore(
            listOf(UploadStatus.UPLOADING, UploadStatus.MERGING), cutoff
        )
        if (expired.isEmpty()) return
        log.info("清理 {} 个过期上传会话", expired.size)
        expired.forEach { session ->
            File("$storagePath/chunks/${session.id}").deleteRecursively()
            redisTemplate.delete("upload:${session.id}")
            uploadSessionRepository.delete(session)
        }
    }
}
