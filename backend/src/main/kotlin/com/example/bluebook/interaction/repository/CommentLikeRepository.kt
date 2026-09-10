package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.CommentLike
import com.example.bluebook.interaction.entity.CommentLikeId
import org.springframework.data.jpa.repository.JpaRepository

interface CommentLikeRepository : JpaRepository<CommentLike, CommentLikeId> {
    fun existsByUserIdAndCommentId(userId: Long, commentId: Long): Boolean
    fun deleteByUserIdAndCommentId(userId: Long, commentId: Long): Int
}
