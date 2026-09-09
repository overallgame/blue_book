package com.example.bluebook.video.repository

import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.entity.VideoStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface VideoRepository : JpaRepository<Video, Long> {
    fun findByIdAndStatus(id: Long, status: VideoStatus): Video?

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

    @Modifying
    @Query("UPDATE Video v SET v.likeCount = v.likeCount + :delta WHERE v.id = :id")
    fun incrementLikeCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.collectCount = v.collectCount + :delta WHERE v.id = :id")
    fun incrementCollectCount(id: Long, delta: Long)

    @Modifying
    @Query("UPDATE Video v SET v.commentCount = v.commentCount + :delta WHERE v.id = :id")
    fun incrementCommentCount(id: Long, delta: Long)

    @Query("SELECT v FROM Video v WHERE v.status = 'PUBLISHED' AND v.transcodeStatus = 'DONE' AND (v.title LIKE %:keyword% OR v.description LIKE %:keyword%) AND (:cursorId IS NULL OR v.id < :cursorId) ORDER BY v.id DESC")
    fun searchVideos(keyword: String, cursorId: Long?, pageable: Pageable): List<Video>
}
