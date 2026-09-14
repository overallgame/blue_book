package com.example.bluebook.notification.service

import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.assetUrl
import com.example.bluebook.notification.dto.NotificationDto
import com.example.bluebook.notification.dto.NotificationListDto
import com.example.bluebook.notification.entity.Notification
import com.example.bluebook.notification.entity.NotifyType
import com.example.bluebook.notification.repository.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) {
    fun list(userId: Long, cursorId: Long?, size: Int): NotificationListDto {
        val pageable = PageRequest.of(0, size)
        val notifications = notificationRepository.findByReceiverId(userId, cursorId, pageable)
        val senderIds = notifications.map { it.senderId }.distinct()
        val senders = userRepository.findAllById(senderIds).associateBy { it.id }
        val items = notifications.map { n ->
            val sender = senders[n.senderId]
            NotificationDto(
                id = n.id,
                type = n.type.name,
                senderId = n.senderId,
                senderNickname = sender?.nickname ?: "用户",
                senderAvatar = assetUrl(sender?.avatarUrl, "upload/images") ?: "",
                videoId = n.videoId,
                commentId = n.commentId,
                content = n.content ?: "",
                isRead = n.isRead,
                // 同 CommentService：createdAt 是本地墙钟时间，不能按 UTC 解释，
                // 否则非 UTC 的 JVM 上时间戳落在未来，客户端恒显示「刚刚」
                createdAt = n.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        return NotificationListDto(items = items, hasMore = notifications.size == size)
    }

    fun unreadCount(userId: Long): Long =
        notificationRepository.countUnreadByReceiverId(userId)

    @Transactional
    fun markAllRead(userId: Long) {
        notificationRepository.markAllReadByReceiverId(userId)
    }

    @Transactional
    fun markRead(userId: Long, notificationId: Long) {
        notificationRepository.markReadByIdAndReceiverId(notificationId, userId)
    }

    @Transactional
    fun delete(userId: Long, notificationId: Long) {
        notificationRepository.deleteByIdAndReceiverId(notificationId, userId)
    }

    @Transactional
    fun clearAll(userId: Long) {
        notificationRepository.deleteAllByReceiverId(userId)
    }

    @Transactional
    fun create(
        receiverId: Long, senderId: Long, type: NotifyType,
        videoId: Long? = null, commentId: Long? = null, content: String? = null
    ) {
        val notification = Notification(
            receiverId = receiverId, senderId = senderId,
            type = type, videoId = videoId, commentId = commentId, content = content
        )
        notificationRepository.save(notification)
    }
}
