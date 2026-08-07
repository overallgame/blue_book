package com.example.bluebook.interaction.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "video_like")
@IdClass(VideoLikeId::class)
class VideoLike(
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Id
    @Column(name = "video_id", nullable = false)
    var videoId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

class VideoLikeId : Serializable {
    var userId: Long = 0
    var videoId: Long = 0
    override fun equals(other: Any?) = other is VideoLikeId && other.userId == userId && other.videoId == videoId
    override fun hashCode() = 31 * userId.hashCode() + videoId.hashCode()
}
