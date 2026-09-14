package com.example.bluebook.common

import com.example.bluebook.auth.repository.RefreshTokenRepository
import com.example.bluebook.auth.repository.UserRepository
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
    private val userRepository: UserRepository,
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
     * 冗余计数（video.like_count 等）平时走原子自增，增量本身正确，但历史脏数据、
     * 异常中断仍会造成漂移，此前这个任务是空的、漂移永不自愈。
     * 这里以互动表为唯一事实来源重算；只记录**实际改动的行数**，便于发现异常。
     */
    @Transactional
    @Scheduled(cron = "0 0 3 * * ?")
    fun dailyReconciliation() {
        log.info("开始每日数据对账...")
        val likes = videoRepository.reconcileLikeCounts()
        val collects = videoRepository.reconcileCollectCounts()
        val comments = videoRepository.reconcileCommentCounts()
        val followers = userRepository.reconcileFollowerCounts()
        val following = userRepository.reconcileFollowingCounts()
        log.info(
            "每日数据对账完成：点赞校正 {} 行、收藏 {} 行、评论 {} 行、粉丝数 {} 行、关注数 {} 行",
            likes, collects, comments, followers, following
        )
    }

    /**
     * 转码兜底：两种卡死形态都不会自愈，需要重新投递。
     * - PROCESSING：doTranscode 先置该状态再执行外部进程，进程重启/被杀后永久停留
     * - PENDING：发布时消息投递失败/被丢弃（事务提交竞态已由 TranscodeTaskPublisher 修掉，
     *   但投递本身仍可能失败），视频会「上传成功却永远不出现」
     * 阈值取 30 分钟：正常转码远快于此，而重复投递的代价只是多跑一次 ffmpeg。
     */
    @Scheduled(fixedRate = 600000)
    fun requeueStaleTranscodes() {
        // 捕获到局部变量：成员属性在 lambda 内无法智能转换
        val rabbit = rabbitTemplate ?: return
        val stale = videoRepository.findStaleTranscodes(
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
