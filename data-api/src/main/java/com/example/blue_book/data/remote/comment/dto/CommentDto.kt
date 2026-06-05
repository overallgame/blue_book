package com.example.blue_book.data.remote.comment.dto

import com.google.gson.annotations.SerializedName

data class CommentDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("videoId") val videoId: Long = 0,
    @SerializedName("userId") val userId: Long = 0,
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("avatar") val avatar: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("likeCount") val likeCount: Int = 0,
    @SerializedName("isLiked") val isLiked: Boolean = false,
    @SerializedName("createTime") val createTime: Long = 0,
    @SerializedName("parentId") val parentId: Long? = null,
    @SerializedName("replyToUserId") val replyToUserId: Long? = null,
    @SerializedName("replyToNickname") val replyToNickname: String? = null
)

data class CommentListDto(
    @SerializedName("items") val items: List<CommentDto> = emptyList(),
    @SerializedName("cursorId") val cursorId: Long? = null,
    @SerializedName("hasMore") val hasMore: Boolean = false
)

data class PostCommentRequestDto(
    @SerializedName("videoId") val videoId: Long,
    @SerializedName("content") val content: String,
    @SerializedName("parentId") val parentId: Long? = null,
    @SerializedName("replyToUserId") val replyToUserId: Long? = null
)
