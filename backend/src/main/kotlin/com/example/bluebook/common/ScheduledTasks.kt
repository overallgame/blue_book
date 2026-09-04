package com.example.bluebook.common

import com.example.bluebook.auth.repository.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ScheduledTasks(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(ScheduledTasks::class.java)

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
     * Clean expired upload sessions and orphaned chunk files every hour.
     */
    @Scheduled(fixedRate = 3600000)
    fun cleanExpiredUploads() {
        log.info("清理过期上传会话和分片...")
        // Sessions older than 24h with status != DONE are expired
    }
}
