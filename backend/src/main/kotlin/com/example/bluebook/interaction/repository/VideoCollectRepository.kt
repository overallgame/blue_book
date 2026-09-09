package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoCollect
import com.example.bluebook.interaction.entity.VideoCollectId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface VideoCollectRepository : JpaRepository<VideoCollect, VideoCollectId> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<VideoCollect>

    /** 视频删除时清理该视频的全部收藏记录 */
    @Modifying
    @Query("DELETE FROM VideoCollect vc WHERE vc.videoId = :videoId")
    fun deleteByVideoId(videoId: Long)
}
