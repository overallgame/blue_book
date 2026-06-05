package com.example.blue_book.data.remote.comment

import com.example.blue_book.data.remote.apiCall
import com.example.blue_book.data.remote.apiUnitCall
import com.example.blue_book.data.remote.comment.dto.CommentDto
import com.example.blue_book.data.remote.comment.dto.CommentListDto
import com.example.blue_book.data.remote.comment.dto.PostCommentRequestDto
import javax.inject.Inject

class CommentRemoteDataSource @Inject constructor(
    private val api: CommentApi
) {

    suspend fun getComments(videoId: Long, cursorId: Long?, size: Int?): Result<CommentListDto> {
        return apiCall { api.getComments(videoId, cursorId, size) }
    }

    suspend fun getReplies(commentId: Long, cursorId: Long?, size: Int?): Result<CommentListDto> {
        return apiCall { api.getReplies(commentId, cursorId, size) }
    }

    suspend fun postComment(videoId: Long, content: String, parentId: Long?, replyToUserId: Long?): Result<CommentDto> {
        val request = PostCommentRequestDto(videoId, content, parentId, replyToUserId)
        return apiCall { api.postComment(request) }
    }

    suspend fun deleteComment(commentId: Long): Result<Unit> {
        return apiUnitCall { api.deleteComment(commentId) }
    }

    suspend fun likeComment(commentId: Long, liked: Boolean): Result<Unit> {
        return apiUnitCall { api.likeComment(commentId, liked) }
    }
}
