package com.example.bluebook.comment.service

import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.comment.dto.CommentDto
import com.example.bluebook.comment.dto.CommentListDto
import com.example.bluebook.comment.dto.PostCommentRequestDto
import com.example.bluebook.comment.entity.Comment
import com.example.bluebook.comment.entity.CommentStatus
import com.example.bluebook.comment.repository.CommentRepository
import com.example.bluebook.common.CommentNotFoundException
import com.example.bluebook.common.assetUrl
import com.example.bluebook.common.ForbiddenException
import com.example.bluebook.notification.entity.NotifyType
import com.example.bluebook.notification.service.NotificationService
import com.example.bluebook.video.repository.VideoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val notificationService: NotificationService
) {
    fun getRootComments(videoId: Long, cursorId: Long?, size: Int, currentUserId: Long?): CommentListDto {
        val pageable = PageRequest.of(0, size)
        val comments = commentRepository.findRootComments(videoId, cursorId, pageable)
        val replyCounts = commentRepository.countByParentIds(comments.map { it.id })
            .associate { row -> (row[0] as Long) to (row[1] as Long).toInt() }
        val items = comments.map { toDto(it, currentUserId, replyCounts[it.id] ?: 0) }
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
        // 楼中楼拍平：回复的目标统一挂到根评论下，replyToUserId 指向被回复的那条评论作者
        val parent = request.parentId?.let { commentRepository.findById(it).orElse(null) }
        val effectiveParentId = parent?.parentId ?: request.parentId
        val replyToUserId = request.replyToUserId ?: parent?.userId
        val comment = Comment(
            videoId = request.videoId,
            userId = userId,
            content = request.content,
            parentId = effectiveParentId,
            replyToUserId = replyToUserId
        )
        commentRepository.save(comment)
        videoRepository.incrementCommentCount(request.videoId, 1L)
        // 通知视频作者
        val video = videoRepository.findById(request.videoId).orElse(null)
        if (video != null && video.uploaderId != userId) {
            notificationService.create(
                receiverId = video.uploaderId, senderId = userId,
                type = NotifyType.COMMENT, videoId = request.videoId,
                commentId = comment.id, content = "评论了你的视频"
            )
        }
        // 如果是回复，通知被回复的评论作者
        if (effectiveParentId != null && replyToUserId != null && replyToUserId != userId) {
            notificationService.create(
                receiverId = replyToUserId, senderId = userId,
                type = NotifyType.COMMENT, videoId = request.videoId,
                commentId = comment.id, content = "回复了你的评论"
            )
        }
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
        if (liked && comment.userId != userId) {
            notificationService.create(
                receiverId = comment.userId, senderId = userId,
                type = NotifyType.LIKE, videoId = comment.videoId,
                commentId = commentId, content = "赞了你的评论"
            )
        }
    }

    private fun toDto(comment: Comment, currentUserId: Long?, replyCount: Int = 0): CommentDto {
        val author = userRepository.findById(comment.userId).orElse(null)
        val replyToUser = comment.replyToUserId?.let { userRepository.findById(it).orElse(null) }
        return CommentDto(
            id = comment.id,
            videoId = comment.videoId,
            userId = comment.userId,
            nickname = author?.nickname ?: "",
            avatar = assetUrl(author?.avatarUrl, "upload/images") ?: "",
            content = comment.content,
            likeCount = comment.likeCount,
            isLiked = false,
            createTime = comment.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
            parentId = comment.parentId,
            replyToUserId = comment.replyToUserId,
            replyToNickname = replyToUser?.nickname,
            replyCount = replyCount
        )
    }
}
