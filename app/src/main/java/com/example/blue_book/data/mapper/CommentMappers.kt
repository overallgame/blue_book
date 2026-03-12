package com.example.blue_book.data.mapper

import com.example.blue_book.di.NetworkModule
import com.example.blue_book.data.remote.comment.dto.CommentDto
import com.example.blue_book.domain.model.Comment

private fun getBaseUrl(): String = NetworkModule.BASE_URL.trimEnd('/')

private fun abs(url: String?): String {
    val u = url?.trim().orEmpty()
    if (u.isBlank()) return ""
    if (u.startsWith("http://") || u.startsWith("https://")) return u
    return if (u.startsWith("/")) "${getBaseUrl()}$u" else "${getBaseUrl()}/$u"
}

private fun CommentDto.toDomain(): Comment {
    return Comment(
        id = id,
        videoId = videoId,
        userId = userId,
        nickname = nickname,
        avatar = abs(avatar),
        content = content,
        likeCount = likeCount,
        isLiked = isLiked,
        createTime = createTime,
        parentId = parentId,
        replyToUserId = replyToUserId,
        replyToNickname = replyToNickname,
        replies = emptyList()
    )
}

fun List<CommentDto>.toDomainComments(): List<Comment> = map { it.toDomain() }

fun CommentDto.toDomainComment(): Comment = toDomain()
