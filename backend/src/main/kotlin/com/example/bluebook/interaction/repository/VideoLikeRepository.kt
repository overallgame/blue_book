package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoLike
import com.example.bluebook.interaction.entity.VideoLikeId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface VideoLikeRepository : JpaRepository<VideoLike, VideoLikeId> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<VideoLike>

    /** 视频删除时清理该视频的全部点赞记录 */
    @Modifying
    @Query("DELETE FROM VideoLike vl WHERE vl.videoId = :videoId")
    fun deleteByVideoId(videoId: Long)
}
