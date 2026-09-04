package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoLike
import com.example.bluebook.interaction.entity.VideoLikeId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface VideoLikeRepository : JpaRepository<VideoLike, VideoLikeId> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<VideoLike>
}
