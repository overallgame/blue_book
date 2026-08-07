package com.example.bluebook.video.dto

import java.time.LocalDateTime

/** Placeholder DTO -- full conversion done in video module. */
data class Video2Dto(
    val id: Long,
    val title: String?,
    val description: String?,
    val coverUrl: String?,
    val hlsUrl: String?,
    val duration: Int?,
    val width: Int?,
    val height: Int?,
    val likeCount: Long,
    val collectCount: Long,
    val commentCount: Long,
    val viewCount: Long,
    val createdAt: LocalDateTime?
)
