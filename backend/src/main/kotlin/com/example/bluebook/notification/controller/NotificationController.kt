package com.example.bluebook.notification.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.notification.entity.Notification
import com.example.bluebook.notification.service.NotificationService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    private fun currentUserId(): Long =
        SecurityContextHolder.getContext().authentication?.principal as? Long ?: 0

    @GetMapping
    fun list(@RequestParam(required = false) cursorId: Long?,
             @RequestParam(defaultValue = "20") size: Int): ApiResponse<List<Notification>> =
        ApiResponse.ok(notificationService.list(currentUserId(), cursorId, size))

    @GetMapping("/unread-count")
    fun unreadCount(): ApiResponse<Long> =
        ApiResponse.ok(notificationService.unreadCount(currentUserId()))

    @PostMapping("/read-all")
    fun markAllRead(): ApiResponse<Any> {
        notificationService.markAllRead(currentUserId())
        return ApiResponse.ok()
    }

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: Long): ApiResponse<Any> {
        notificationService.markRead(currentUserId(), id)
        return ApiResponse.ok()
    }
}
