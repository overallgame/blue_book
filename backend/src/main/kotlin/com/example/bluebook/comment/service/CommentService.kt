package com.example.bluebook.comment.service

import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.comment.dto.CommentDto
import com.example.bluebook.comment.dto.CommentListDto
import com.example.bluebook.comment.dto.PostCommentRequestDto
import com.example.bluebook.comment.entity.Comment
import com.example.bluebook.comment.entity.CommentStatus
import com.example.bluebook.comment.repository.CommentRepository
import com.example.bluebook.common.CommentNotFoundException
import com.example.bluebook.common.ForbiddenException
import com.example.bluebook.video.repository.VideoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository
) {
    fun getRootComments(videoId: Long, cursorId: Long?, size: Int, currentUserId: Long?): CommentListDto {
        val pageable = PageRequest.of(0, size)
        val comments = commentRepository.findRootComments(videoId, cursorId, pageable)
        val items = comments.map { toDto(it, currentUserId) }
        val hasMore = items.size == size
        return CommentListDto(items = items, cursorId = items.lastOrNull()?.id, hasMore = hasMore)
    }

    fun getReplies(parentId: Long, cursorId: Long?, size: Int, currentUserId: Long?): CommentListDto {
        val pageable = PageRequest.of(0, size)
        val replies = commentRepository.findReplies(parentId, cursorId, pageable)
        val items = replies.map { toDto(it, currentUserId) }
        val hasMore = items.size == size
        return CommentListDto(items = items, cursorId = items.lastOrNull()?.id, hasMore = hasMore)
    }

    @Transactional
    fun postComment(userId: Long, request: PostCommentRequestDto): CommentDto {
        val comment = Comment(
            videoId = request.videoId,
            userId = userId,
            content = request.content,
            parentId = request.parentId,
            replyToUserId = request.replyToUserId
        )
        commentRepository.save(comment)
        videoRepository.incrementCommentCount(request.videoId, 1L)
        return toDto(comment, userId)
    }

    @Transactional
    fun deleteComment(userId: Long, commentId: Long) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException() }
        if (comment.userId != userId) throw ForbiddenException()
        comment.status = CommentStatus.DELETED
        commentRepository.save(comment)
        videoRepository.incrementCommentCount(comment.videoId, -1L)
    }

    @Transactional
    fun likeComment(userId: Long, commentId: Long, liked: Boolean) {
        val comment = commentRepository.findByIdAndStatus(commentId, CommentStatus.NORMAL)
            ?: throw CommentNotFoundException()
        val delta = if (liked) 1 else -1
        commentRepository.incrementLikeCount(commentId, delta)
    }

    private fun toDto(comment: Comment, currentUserId: Long?): CommentDto {
        val author = userRepository.findById(comment.userId).orElse(null)
        val replyToUser = comment.replyToUserId?.let { userRepository.findById(it).orElse(null) }
        return CommentDto(
            id = comment.id,
            videoId = comment.videoId,
            userId = comment.userId,
            nickname = author?.nickname ?: "",
            avatar = author?.avatarUrl ?: "",
            content = comment.content,
            likeCount = comment.likeCount,
            isLiked = false,
            createTime = comment.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
            parentId = comment.parentId,
            replyToUserId = comment.replyToUserId,
            replyToNickname = replyToUser?.nickname
        )
    }
}
