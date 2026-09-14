package com.example.bluebook.video.repository

import com.example.bluebook.video.entity.TranscodeStatus
import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.entity.VideoStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface VideoRepository : JpaRepository<Video, Long> {
    fun findByIdAndStatus(id: Long, status: VideoStatus): Video?

    /**
     * 卡住的转码任务，按状态取不同阈值：
     * - `PENDING` 用短阈值（消息投递失败/被丢弃，恢复不需要任何保护）
     * - `PROCESSING` 用长阈值（必须大于任何合理的转码耗时，否则会与仍在运行的 ffmpeg 并发写）
     *
     * 判定依据是 `transcodeUpdatedAt`（只由转码路径写入）而不是 `updatedAt`：
     * 点赞/播放/评论都会顶掉 `updatedAt`，用它做判据会让卡死的任务永远不"过期"。
     * 历史行的该列为 NULL，回退到 `createdAt`。
     */
    @Query(
        """
        SELECT v FROM Video v
        WHERE (v.transcodeStatus = 'PENDING'
               AND COALESCE(v.transcodeUpdatedAt, v.createdAt) < :pendingThreshold)
           OR (v.transcodeStatus = 'PROCESSING'
               AND COALESCE(v.transcodeUpdatedAt, v.createdAt) < :processingThreshold)
        """
    )
    fun findStaleTranscodes(
        pendingThreshold: LocalDateTime,
        processingThreshold: LocalDateTime
    ): List<Video>

    /**
     * 把卡住的转码任务重置为 PENDING 并推进 transcodeUpdatedAt（否则下一轮又会命中）。
     * 带状态复查：返回 0 表示该行已被其它路径处理（例如重复消息刚把它跑完），
     * 此时调用方不应再投递消息。用定向 UPDATE 而非 save(detached 实体)，
     * 避免把读取时的整行快照写回、回退期间累计的计数。
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE Video v SET v.transcodeStatus = 'PENDING', v.transcodeUpdatedAt = CURRENT_TIMESTAMP
        WHERE v.id = :id AND v.transcodeStatus IN ('PENDING','PROCESSING')
        """
    )
    fun markStaleTranscodePending(id: Long): Int

    /** 转码开始/失败只改状态，不动 hlsUrl/coverUrl */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE Video v SET v.transcodeStatus = :status, v.transcodeUpdatedAt = CURRENT_TIMESTAMP
        WHERE v.id = :id
        """
    )
    fun updateTranscodeStatus(id: Long, status: TranscodeStatus): Int

    /** 转码成功：一次写入状态与产物地址 */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE Video v SET v.transcodeStatus = :status, v.hlsUrl = :hlsUrl, v.coverUrl = :coverUrl,
               v.transcodeUpdatedAt = CURRENT_TIMESTAMP
        WHERE v.id = :id
        """
    )
    fun updateTranscodeDone(id: Long, status: TranscodeStatus, hlsUrl: String?, coverUrl: String?): Int

    // ========== 每日对账：按互动表重算冗余计数 ==========
    // 这些计数走原子自增（增量正确），但历史脏数据、异常中断仍可能造成漂移，
    // 且没有任何地方会自动修正，故由每日任务以互动表为唯一事实来源重算。
    //
    // 三点关键设计：
    // 1) WHERE 只匹配**确实漂移**的行。这样返回的行数就是漂移行数（可直接用于告警），
    //    同时避免给全表加锁、也避免把未变化的行的 updatedAt 顶掉
    //    （updatedAt 还被转码卡死检测使用）。
    // 2) 每个方法各自 @Transactional：五条语句分开提交，任一条失败不会让其它四条一起回滚，
    //    也把锁持有时间从「五条语句的总和」缩到单条语句。
    // 3) SET 子句里的列不加表别名限定（`SET like_count = ...` 而非 `SET v.like_count = ...`），
    //    这是最保守的 MySQL 语法形式。

    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE video SET like_count = (SELECT COUNT(*) FROM video_like l WHERE l.video_id = video.id)
            WHERE like_count IS NULL
               OR like_count <> (SELECT COUNT(*) FROM video_like l WHERE l.video_id = video.id)
        """,
        nativeQuery = true
    )
    fun reconcileLikeCounts(): Int

    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE video SET collect_count = (SELECT COUNT(*) FROM video_collect c WHERE c.video_id = video.id)
            WHERE collect_count IS NULL
               OR collect_count <> (SELECT COUNT(*) FROM video_collect c WHERE c.video_id = video.id)
        """,
        nativeQuery = true
    )
    fun reconcileCollectCounts(): Int

    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE video SET comment_count = (SELECT COUNT(*) FROM comment c WHERE c.video_id = video.id AND c.status = 'NORMAL')
            WHERE comment_count IS NULL
               OR comment_count <> (SELECT COUNT(*) FROM comment c WHERE c.video_id = video.id AND c.status = 'NORMAL')
        """,
        nativeQuery = true
    )
    fun reconcileCommentCounts(): Int

    fun findByUploaderIdAndStatus(uploaderId: Long, status: VideoStatus, pageable: Pageable): List<Video>

    @Query("SELECT v FROM Video v WHERE v.status = 'PUBLISHED' AND v.transcodeStatus = 'DONE' AND (:cursorId IS NULL OR v.id < :cursorId) ORDER BY v.id DESC")
    fun findFeedVideos(cursorId: Long?, pageable: Pageable): List<Video>

    /** 我点赞的视频：按视频 id 倒序游标分页（与 feed 同序），避免 join 后乱序与 filter 缩水 */
    @Query("""
        SELECT v FROM Video v JOIN VideoLike l ON l.videoId = v.id
        WHERE l.userId = :userId AND v.status = 'PUBLISHED'
        AND (:cursorId IS NULL OR v.id < :cursorId)
        ORDER BY v.id DESC
    """)
    fun findLikedVideosByUser(userId: Long, cursorId: Long?, pageable: Pageable): List<Video>

    /** 我收藏的视频：按视频 id 倒序游标分页 */
    @Query("""
        SELECT v FROM Video v JOIN VideoCollect c ON c.videoId = v.id
        WHERE c.userId = :userId AND v.status = 'PUBLISHED'
        AND (:cursorId IS NULL OR v.id < :cursorId)
        ORDER BY v.id DESC
    """)
    fun findCollectedVideosByUser(userId: Long, cursorId: Long?, pageable: Pageable): List<Video>

    /** 某用户的全部作品（含转码中，便于发布后立即可见）：按 id 倒序游标分页 */
    @Query("""
        SELECT v FROM Video v
        WHERE v.uploaderId = :uploaderId AND v.status = 'PUBLISHED'
        AND (:cursorId IS NULL OR v.id < :cursorId)
        ORDER BY v.id DESC
    """)
    fun findUserVideosCursor(uploaderId: Long, cursorId: Long?, pageable: Pageable): List<Video>

    /** 关注流：当前用户关注的所有作者的作品（转码完成才可播，与 feed 同过滤标准），按 id 倒序游标分页 */
    @Query("""
        SELECT v FROM Video v, UserFollow f
        WHERE f.followeeId = v.uploaderId AND f.followerId = :userId
        AND v.status = 'PUBLISHED' AND v.transcodeStatus = 'DONE'
        AND (:cursorId IS NULL OR v.id < :cursorId)
        ORDER BY v.id DESC
    """)
    fun findFollowingFeedVideos(userId: Long, cursorId: Long?, pageable: Pageable): List<Video>

    /** 某作者已发布作品的获赞与收藏合计（[0]=点赞合计，[1]=收藏合计） */
    @Query("""
        SELECT COALESCE(SUM(v.likeCount), 0), COALESCE(SUM(v.collectCount), 0)
        FROM Video v WHERE v.uploaderId = :uploaderId AND v.status = 'PUBLISHED'
    """)
    fun sumInteractionsByUploader(uploaderId: Long): Array<Any>

    /** 本地流：按发布地区过滤（转码完成才可播），按 id 倒序游标分页 */
    @Query("""
        SELECT v FROM Video v
        WHERE v.region = :region AND v.status = 'PUBLISHED' AND v.transcodeStatus = 'DONE'
        AND (:cursorId IS NULL OR v.id < :cursorId)
        ORDER BY v.id DESC
    """)
    fun findRegionFeedVideos(region: String, cursorId: Long?, pageable: Pageable): List<Video>

    @Modifying
    @Query("UPDATE Video v SET v.likeCount = v.likeCount + :delta WHERE v.id = :id")
    fun incrementLikeCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.collectCount = v.collectCount + :delta WHERE v.id = :id")
    fun incrementCollectCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.commentCount = v.commentCount + :delta WHERE v.id = :id")
    fun incrementCommentCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + :delta WHERE v.id = :id")
    fun incrementViewCount(id: Long, delta: Long)

    @Query("SELECT v FROM Video v WHERE v.status = 'PUBLISHED' AND v.transcodeStatus = 'DONE' AND (v.title LIKE %:keyword% OR v.description LIKE %:keyword%) AND (:cursorId IS NULL OR v.id < :cursorId) ORDER BY v.id DESC")
    fun searchVideos(keyword: String, cursorId: Long?, pageable: Pageable): List<Video>
}
