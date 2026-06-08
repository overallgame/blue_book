package com.example.blue_book.data.repository

import com.example.blue_book.data.mapper.toDomainComment
import com.example.blue_book.data.mapper.toDomainComments
import com.example.blue_book.data.remote.comment.CommentRemoteDataSource
import com.example.blue_book.domain.model.Comment
import com.example.blue_book.domain.repository.CommentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val remote: CommentRemoteDataSource
) : CommentRepository {

    override suspend fun fetchRootComments(
        videoId: Long,
        cursorId: Long?,
        size: Int
    ): Result<List<Comment>> {
        val result = remote.getComments(videoId, cursorId, size)
        return result.map { it.items.toDomainComments() }
    }

    override suspend fun fetchReplies(
        parentId: Long,
        cursorId: Long?,
        size: Int
    ): Result<List<Comment>> {
        val result = remote.getReplies(parentId, cursorId, size)
        return result.map { it.items.toDomainComments() }
    }

    override suspend fun postComment(videoId: Long, content: String): Result<Comment> {
        val result = remote.postComment(videoId, content, null, null)
        return result.map { it.toDomainComment() }
    }

    override suspend fun replyComment(
        parentId: Long,
        videoId: Long,
        content: String,
        replyToUserId: Long
    ): Result<Comment> {
        val result = remote.postComment(videoId, content, parentId, replyToUserId)
        return result.map { it.toDomainComment() }
    }

    override suspend fun likeComment(commentId: Long, liked: Boolean): Result<Unit> {
        return remote.likeComment(commentId, liked)
    }

    override suspend fun deleteComment(commentId: Long): Result<Unit> {
        return remote.deleteComment(commentId)
    }
}
