package com.example.bluebook.comment.dto

data class PostCommentRequestDto(
    val videoId: Long,
    val content: String,
    val parentId: Long? = null,
    val replyToUserId: Long? = null
)
