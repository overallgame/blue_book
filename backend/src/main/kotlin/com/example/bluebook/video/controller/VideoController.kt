package com.example.bluebook.video.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.video.dto.*
import com.example.bluebook.video.service.VideoService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
class VideoController(private val videoService: VideoService) {
    private fun currentUserId(): Long =
        SecurityContextHolder.getContext().authentication?.principal as? Long ?: 0

    private fun optionalUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)?.takeIf { it > 0 }

    @GetMapping("/api/v2/feed")
    fun feed(@RequestParam(required = false) cursorId: Long?,
             @RequestParam(defaultValue = "10") size: Int): ApiResponse<FeedResponseDto> =
        ApiResponse.ok(videoService.feed(cursorId, size, optionalUserId()))

    @GetMapping("/api/v2/videos/{id}/dto")
    fun getVideoDto(@PathVariable id: Long): ApiResponse<Video2Dto> =
        ApiResponse.ok(videoService.getVideoDto(id, optionalUserId()))

    @GetMapping("/api/v2/videos/{id}/playUrl")
    fun getPlayUrl(@PathVariable id: Long, @RequestParam cid: Long): ApiResponse<String> =
        ApiResponse.ok(videoService.getPlayUrl(id, cid))

    @GetMapping("/api/v2/videos/{id}/status")
    fun getTranscodeStatus(@PathVariable id: Long): ApiResponse<String> =
        ApiResponse.ok(videoService.getTranscodeStatus(id))

    @PostMapping("/api/v2/videos/publish")
    fun publish(@RequestBody request: PublishRequest): ApiResponse<Video2Dto> =
        ApiResponse.ok(videoService.publish(currentUserId(), request))

    @PostMapping("/api/v2/videos/{id}/like")
    fun likeVideo(@PathVariable id: Long, @RequestParam liked: Boolean): ApiResponse<Any> {
        videoService.likeVideo(currentUserId(), id, liked)
        return ApiResponse.ok()
    }

    @PostMapping("/api/v2/videos/{id}/collect")
    fun collectVideo(@PathVariable id: Long, @RequestParam collected: Boolean): ApiResponse<Any> {
        videoService.collectVideo(currentUserId(), id, collected)
        return ApiResponse.ok()
    }
}
