package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.VideoCollect
import com.example.bluebook.interaction.entity.VideoCollectId
import org.springframework.data.jpa.repository.JpaRepository

interface VideoCollectRepository : JpaRepository<VideoCollect, VideoCollectId> {
    fun existsByUserIdAndVideoId(userId: Long, videoId: Long): Boolean
    fun deleteByUserIdAndVideoId(userId: Long, videoId: Long): Int
    fun countByVideoId(videoId: Long): Long
}
