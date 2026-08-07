package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoLike
import org.springframework.data.jpa.repository.JpaRepository

interface VideoLikeRepository : JpaRepository<VideoLike, Long> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
}
