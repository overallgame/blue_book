package com.example.bluebook.comment.repository

import com.example.bluebook.comment.entity.Comment
import com.example.bluebook.comment.entity.CommentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CommentRepository : JpaRepository<Comment, Long> {
    @Query("SELECT c FROM Comment c WHERE c.videoId = :videoId AND c.parentId IS NULL AND c.status = 'NORMAL' AND (:cursorId IS NULL OR c.id < :cursorId) ORDER BY c.id DESC")
    fun findRootComments(videoId: Long, cursorId: Long?, pageable: Pageable): List<Comment>

    @Query("SELECT c FROM Comment c WHERE c.parentId = :parentId AND c.status = 'NORMAL' AND (:cursorId IS NULL OR c.id < :cursorId) ORDER BY c.id ASC")
    fun findReplies(parentId: Long, cursorId: Long?, pageable: Pageable): List<Comment>

    fun findByIdAndStatus(id: Long, status: CommentStatus): Comment?

    @Modifying
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + :delta WHERE c.id = :id")
    fun incrementLikeCount(id: Long, delta: Int)
}
