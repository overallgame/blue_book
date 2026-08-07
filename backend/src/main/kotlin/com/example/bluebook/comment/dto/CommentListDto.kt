package com.example.bluebook.comment.dto

data class CommentListDto(
    val items: List<CommentDto>,
    val nextCursorId: Long?
)
