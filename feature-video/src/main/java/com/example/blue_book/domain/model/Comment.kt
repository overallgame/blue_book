package com.example.blue_book.domain.model

data class Comment(
    val id: Long = 0,
    val videoId: Long = 0,
    val userId: Long = 0,
    val nickname: String = "",
    val avatar: String = "",
    val content: String = "",
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val createTime: Long = 0,
    val parentId: Long? = null,
    val replyToUserId: Long? = null,
    val replyToNickname: String? = null,
    val replies: List<Comment> = emptyList()
) {
    val isRoot: Boolean get() = parentId == null
    val isReply: Boolean get() = parentId != null
    val replyCount: Int get() = replies.size
}
