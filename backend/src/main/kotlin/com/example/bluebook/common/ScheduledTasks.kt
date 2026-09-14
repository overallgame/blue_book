package com.example.bluebook.common

import com.example.bluebook.auth.repository.RefreshTokenRepository
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.file.entity.UploadStatus
import com.example.bluebook.file.repository.UploadSessionRepository
import com.example.bluebook.video.repository.VideoRepository
import com.example.bluebook.video.service.TranscodeRegistry
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
    private val transcodeRegistry: TranscodeRegistry,
    private val rabbitTemplate: RabbitTemplate? = null,
    @Value("\${app.upload.storage-path}") private val storagePath: String
) {
    private val log = LoggerFactory.getLogger(ScheduledTasks::class.java)

    private companion object {
        /**
         * PENDING 卡死（消息投递失败/被丢弃）：恢复不需要任何保护，短阈值即可。
         */
        const val STALE_PENDING_MINUTES = 30L

        /**
         * PROCESSING 卡死：必须**远大于**任何合理的转码耗时。因为进程重启后
         * [TranscodeRegistry] 的内存记录会丢失，此时可能对仍在运行的孤儿 ffmpeg
         * 重复投递——两个 ffmpeg 会用 `-y` 并发写同一组 HLS 切片。
         */
        const val STALE_PROCESSING_MINUTES = 120L

        /** 未完成上传会话的保留时长（按最后活动时间计，见 ChunkUploadService） */
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
     * 每日数据对账：以互动表为唯一事实来源重算冗余计数。
     *
     * **刻意不加 `@Transactional`**：五个 `@Modifying` 方法各自带 `@Transactional`，
     * 于是五条语句分别提交——任一条失败不会把其它四条一起回滚，锁的持有时间也从
     * 「五条语句之和」缩到单条语句。原先放在一个大事务里会在凌晨锁住整张 video 表。
     *
     * 日志里的数字是**实际漂移（并已修正）的行数**：每个对账语句都带
     * `WHERE 计数 <> 重算值`，只匹配真正不一致的行，因此无论 Connector/J 的
     * `useAffectedRows` 如何设置，返回的都是漂移行数。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    fun dailyReconciliation() {
        log.info("开始每日数据对账...")
        val likes = videoRepository.reconcileLikeCounts()
        val collects = videoRepository.reconcileCollectCounts()
        val comments = videoRepository.reconcileCommentCounts()
        val followers = userRepository.reconcileFollowerCounts()
        val following = userRepository.reconcileFollowingCounts()
        val total = likes + collects + comments + followers + following
        if (total == 0) {
            log.info("每日数据对账完成：无漂移")
        } else {
            // 有漂移时用 warn，便于在日志里被注意到
            log.warn(
                "每日数据对账完成：点赞 {}、收藏 {}、评论 {}、粉丝数 {}、关注数 {}（共 {} 行漂移已修正）",
                likes, collects, comments, followers, following, total
            )
        }
    }

    /**
     * 转码兜底：把已经死掉的任务重新投递。
     *
     * 两种卡死形态都不会自愈：
     *  - PROCESSING 且 worker 已被杀（进程重启/崩溃）
     *  - PENDING 且消息投递失败或被丢弃
     *
     * **绝不能重投仍在运行的任务**：`transcode-worker.sh` 用 `-y` 写同一组 HLS 切片，
     * 两个 ffmpeg 并发写会产出损坏的播放列表。因此这里用 [TranscodeRegistry]
     * 排掉本进程正在处理的 id——数据库状态本身无法区分「在跑」与「已死」。
     */
    @Scheduled(fixedRate = 600000)
    fun requeueStaleTranscodes() {
        // 捕获到局部变量：成员属性在 lambda 内无法智能转换
        val rabbit = rabbitTemplate ?: return
        val now = LocalDateTime.now()
        val stale = videoRepository.findStaleTranscodes(
            pendingThreshold = now.minusMinutes(STALE_PENDING_MINUTES),
            processingThreshold = now.minusMinutes(STALE_PROCESSING_MINUTES)
        )
        // 排掉本进程正在运行的（这些是「慢」不是「死」）
        val dead = stale.filterNot { transcodeRegistry.isRunning(it.id) }
        if (dead.isEmpty()) return

        log.warn("发现 {} 条卡住的转码任务，重新投递", dead.size)
        dead.forEach { video ->
            // 定向更新 + 状态复查：返回 0 表示该行已被其它路径处理（例如重复消息刚跑完），
            // 此时不能再投递，否则会对一个已 DONE 的视频重复起 ffmpeg
            val updated = videoRepository.markStaleTranscodePending(video.id)
            if (updated == 0) {
                log.info("转码任务已被其它路径处理，跳过重投: videoId={}", video.id)
                return@forEach
            }
            runCatching { rabbit.convertAndSend("video.transcode", video.id) }
                .onFailure { log.error("重新投递失败: videoId={}", video.id, it) }
        }
    }

    /**
     * 清理过期上传会话与遗留分片。
     * 分片目录 chunks/{uploadId} 此前只在合并成功时删除，中断的上传会永久占盘。
     * 过期按 `updated_at`（最后活动时间）判断——`uploadChunk` 与续传分支都会刷新它。
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
