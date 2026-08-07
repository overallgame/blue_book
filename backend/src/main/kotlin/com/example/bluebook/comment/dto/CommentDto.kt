package com.example.bluebook.comment.dto

data class CommentDto(
    val id: Long,
    val videoId: Long,
    val userId: Long,
    val nickname: String,
    val avatar: String?,
    val content: String,
    val likeCount: Int,
    val isLiked: Boolean,
    val createTime: Long,
    val parentId: Long? = null,
    val replyToUserId: Long? = null,
    val replyToNickname: String? = null,
    val replies: List<CommentDto> = emptyList()
)
