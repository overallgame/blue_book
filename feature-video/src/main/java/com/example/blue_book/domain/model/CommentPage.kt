package com.example.blue_book.domain.model

/** 评论/回复分页结果，cursorId 与 hasMore 透传服务端游标语义 */
data class CommentPage(
	val comments: List<Comment> = emptyList(),
	val cursorId: Long? = null,
	val hasMore: Boolean = false
)
