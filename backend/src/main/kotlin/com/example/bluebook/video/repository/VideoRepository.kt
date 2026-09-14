package com.example.bluebook.video.repository

import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.entity.VideoStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface VideoRepository : JpaRepository<Video, Long> {
    fun findByIdAndStatus(id: Long, status: VideoStatus): Video?

    /**
     * 卡住的转码任务，两种形态都不会自愈：
     * - PROCESSING：doTranscode 先置该状态再跑外部进程，进程重启/被杀后永久停留
     * - PENDING：发布时事务尚未提交就投递了 MQ 消息，消费者查不到行会丢弃消息
     * 而 feed 只显示 transcodeStatus = DONE 的视频，所以这些视频会「上传成功却永远不出现」。
     */
    @Query("SELECT v FROM Video v WHERE v.transcodeStatus IN ('PENDING','PROCESSING') AND v.updatedAt < :threshold")
    fun findStaleTranscodes(threshold: LocalDateTime): List<Video>

    // ========== 每日对账：按互动表重算冗余计数 ==========
    // 这些计数走原子自增（增量正确），但历史脏数据、异常中断仍可能造成漂移，
    // 且没有任何地方会自动修正，故由每日任务以互动表为唯一事实来源重算。

    @Modifying
    @Query(
        value = "UPDATE video v SET v.like_count = (SELECT COUNT(*) FROM video_like l WHERE l.video_id = v.id)",
        nativeQuery = true
    )
    fun reconcileLikeCounts(): Int

    @Modifying
    @Query(
        value = "UPDATE video v SET v.collect_count = (SELECT COUNT(*) FROM video_collect c WHERE c.video_id = v.id)",
        nativeQuery = true
    )
    fun reconcileCollectCounts(): Int

    @Modifying
    @Query(
        value = "UPDATE video v SET v.comment_count = (SELECT COUNT(*) FROM comment c WHERE c.video_id = v.id AND c.status = 'NORMAL')",
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
