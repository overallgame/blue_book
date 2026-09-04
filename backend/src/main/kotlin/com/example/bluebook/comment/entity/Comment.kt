package com.example.bluebook.comment.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "comment", indexes = [
    Index(name = "idx_video_parent", columnList = "video_id,parent_id,created_at")
])
class Comment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "video_id", nullable = false)
    var videoId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "parent_id")
    var parentId: Long? = null,

    @Column(name = "reply_to_user_id")
    var replyToUserId: Long? = null,

    @Column(name = "content", nullable = false, length = 1000)
    var content: String,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: CommentStatus = CommentStatus.NORMAL,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

enum class CommentStatus { NORMAL, DELETED }
