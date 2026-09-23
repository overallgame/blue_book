package com.example.blue_book.data.mapper

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.data.remote.comment.dto.CommentDto
import com.example.blue_book.domain.model.Comment
import com.example.blue_book.util.absoluteUrl

/** 空/空串 → ""（调用方直接喂给 Glide）；已经是绝对地址的原样返回 */
private fun abs(url: String?): String = absoluteUrl(ApiGateway.BASE_URL, url) ?: ""

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
		replyCount = replyCount,
		replies = emptyList()
	)
}

fun List<CommentDto>.toDomainComments(): List<Comment> = map { it.toDomain() }

fun CommentDto.toDomainComment(): Comment = toDomain()
