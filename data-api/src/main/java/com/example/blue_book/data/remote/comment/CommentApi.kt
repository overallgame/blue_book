package com.example.blue_book.data.remote.comment

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.remote.comment.dto.CommentDto
import com.example.blue_book.data.remote.comment.dto.CommentListDto
import com.example.blue_book.data.remote.comment.dto.PostCommentRequestDto
import retrofit2.Response
import retrofit2.http.*

interface CommentApi {

    @GET("/api/v1/comments")
    suspend fun getComments(
        @Query("videoId") videoId: Long,
        @Query("cursorId") cursorId: Long? = null,
        @Query("size") size: Int? = null
    ): Response<ApiResponse<CommentListDto>>

    @GET("/api/v1/comments/{commentId}/replies")
    suspend fun getReplies(
        @Path("commentId") commentId: Long,
        @Query("cursorId") cursorId: Long? = null,
        @Query("size") size: Int? = null
    ): Response<ApiResponse<CommentListDto>>

    @POST("/api/v1/comments")
    suspend fun postComment(
        @Body request: PostCommentRequestDto
    ): Response<ApiResponse<CommentDto>>

    @DELETE("/api/v1/comments/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: Long
    ): Response<ApiResponse<Any>>

    @POST("/api/v1/comments/{commentId}/like")
    suspend fun likeComment(
        @Path("commentId") commentId: Long,
        @Query("liked") liked: Boolean
    ): Response<ApiResponse<Any>>
}
