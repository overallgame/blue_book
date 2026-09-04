package com.example.bluebook.notification.service

import com.example.bluebook.notification.entity.Notification
import com.example.bluebook.notification.entity.NotifyType
import com.example.bluebook.notification.repository.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository
) {
    fun list(userId: Long, cursorId: Long?, size: Int): List<Notification> =
        notificationRepository.findByReceiverId(userId, cursorId, PageRequest.of(0, size))

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
