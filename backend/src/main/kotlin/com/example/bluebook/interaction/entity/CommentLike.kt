package com.example.bluebook.interaction.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "comment_like")
@IdClass(CommentLikeId::class)
class CommentLike(
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Id
    @Column(name = "comment_id", nullable = false)
    var commentId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

class CommentLikeId : Serializable {
    var userId: Long = 0
    var commentId: Long = 0
    override fun equals(other: Any?) = other is CommentLikeId && other.userId == userId && other.commentId == commentId
    override fun hashCode() = 31 * userId.hashCode() + commentId.hashCode()
}
