package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.Comment

interface CommentRepository {
    suspend fun fetchRootComments(videoId: Long, cursorId: Long?, size: Int): Result<List<Comment>>
    suspend fun fetchReplies(parentId: Long, cursorId: Long?, size: Int): Result<List<Comment>>
    suspend fun postComment(videoId: Long, content: String): Result<Comment>
    suspend fun replyComment(parentId: Long, videoId: Long, content: String, replyToUserId: Long): Result<Comment>
    suspend fun likeComment(commentId: Long, liked: Boolean): Result<Unit>
    suspend fun deleteComment(commentId: Long): Result<Unit>
}
