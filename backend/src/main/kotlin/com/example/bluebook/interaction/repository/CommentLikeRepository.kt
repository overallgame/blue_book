package com.example.bluebook.interaction.repository

import com.example.bluebook.interaction.entity.CommentLike
import com.example.bluebook.interaction.entity.CommentLikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface CommentLikeRepository : JpaRepository<CommentLike, CommentLikeId> {
    fun existsByUserIdAndCommentId(userId: Long, commentId: Long): Boolean

    /**
     * 取消点赞。用批量 DELETE 而不是派生删除：后者先 getResultList() 再逐条 em.remove，
     * 返回的是**查到**的行数而非删除数，于是并发的两次取消点赞都会认为删掉了一行、
     * 各发出一次计数递减（与关注数那次修复同一类问题）。
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.userId = :userId AND cl.commentId = :commentId")
    fun deleteLike(userId: Long, commentId: Long): Int
}
