package com.example.bluebook.notification.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notification", indexes = [
    Index(name = "idx_receiver_read", columnList = "receiver_id,is_read,created_at")
])
class Notification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "receiver_id", nullable = false)
    var receiverId: Long,

    @Column(name = "sender_id", nullable = false)
    var senderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: NotifyType,

    @Column(name = "video_id")
    var videoId: Long? = null,

    @Column(name = "comment_id")
    var commentId: Long? = null,

    @Column(name = "content", length = 500)
    var content: String? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

enum class NotifyType { LIKE, COMMENT, FOLLOW, SYSTEM }
