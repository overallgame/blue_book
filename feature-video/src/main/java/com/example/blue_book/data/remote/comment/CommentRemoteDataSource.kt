package com.example.blue_book.data.remote.comment

import com.example.blue_book.data.remote.comment.dto.CommentDto
import com.example.blue_book.data.remote.comment.dto.CommentListDto
import com.example.blue_book.data.remote.comment.dto.PostCommentRequestDto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

class CommentRemoteDataSource @Inject constructor(
    private val apiGateway: ApiGateway
) {
    private val api = apiGateway.createApi(CommentApi::class.java)

    suspend fun getComments(videoId: Long, cursorId: Long?, size: Int?): Result<CommentListDto> =
        apiGateway.apiResult { api.getComments(videoId, cursorId, size) }

    suspend fun getReplies(commentId: Long, cursorId: Long?, size: Int?): Result<CommentListDto> =
        apiGateway.apiResult { api.getReplies(commentId, cursorId, size) }

    suspend fun postComment(videoId: Long, content: String, parentId: Long?, replyToUserId: Long?): Result<CommentDto> {
        val request = PostCommentRequestDto(videoId, content, parentId, replyToUserId)
        return apiGateway.apiResult { api.postComment(request) }
    }

    suspend fun deleteComment(commentId: Long): Result<Unit> =
        apiGateway.apiUnitResult { api.deleteComment(commentId) }

    suspend fun likeComment(commentId: Long, liked: Boolean): Result<Unit> =
        apiGateway.apiUnitResult { api.likeComment(commentId, liked) }
}
