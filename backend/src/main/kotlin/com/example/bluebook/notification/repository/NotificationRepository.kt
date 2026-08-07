package com.example.bluebook.notification.repository

import com.example.bluebook.notification.entity.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationRepository : JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.receiverId = :userId AND (:cursorId IS NULL OR n.id < :cursorId) ORDER BY n.id DESC")
    fun findByReceiverId(userId: Long, cursorId: Long?, pageable: Pageable): List<Notification>

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.receiverId = :userId AND n.isRead = false")
    fun countUnreadByReceiverId(userId: Long): Long

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiverId = :userId AND n.isRead = false")
    fun markAllReadByReceiverId(userId: Long)

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.receiverId = :userId")
    fun markReadByIdAndReceiverId(id: Long, userId: Long): Int
}
