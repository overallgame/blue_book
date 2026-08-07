package com.example.bluebook.user.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.user.dto.*
import com.example.bluebook.user.service.UserService
import com.example.bluebook.video.dto.Video2Dto
import com.example.bluebook.video.entity.VideoStatus
import com.example.bluebook.video.repository.VideoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
class UserController(
    private val userService: UserService,
    private val videoRepository: VideoRepository
) {
    private fun currentUserId(): Long =
        SecurityContextHolder.getContext().authentication?.principal as? Long ?: 0

    private fun optionalUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)?.takeIf { it > 0 }

    @GetMapping("/api/v2/me")
    fun me(): ApiResponse<UserV2MeDto> = ApiResponse.ok(userService.me(currentUserId()))

    @PutMapping("/api/v2/me")
    fun updateMe(@RequestBody request: UserV2UpdateRequestDto): ApiResponse<UserV2MeDto> =
        ApiResponse.ok(userService.updateMe(currentUserId(), request))

    @PostMapping("/api/v2/me/avatar")
    fun uploadAvatar(): ApiResponse<UserV2AvatarUploadResponseDto> {
        // File upload handled separately; placeholder for now
        return ApiResponse.ok(UserV2AvatarUploadResponseDto(""))
    }

    @GetMapping("/api/v2/users/{id}")
    fun profile(@PathVariable id: Long): ApiResponse<UserV2ProfileDto> =
        ApiResponse.ok(userService.profile(id, optionalUserId()))

    @GetMapping("/api/v2/users/{id}/videos")
    fun userVideos(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<List<Video2Dto>> {
        videoRepository.findByUploaderIdAndStatus(id, VideoStatus.PUBLISHED, PageRequest.of(0, size))
        // Placeholder: full DTO conversion done in video module
        return ApiResponse.ok(emptyList())
    }

    @PostMapping("/api/v2/users/{id}/follow")
    fun follow(@PathVariable id: Long): ApiResponse<Any> {
        userService.follow(currentUserId(), id)
        return ApiResponse.ok()
    }

    @DeleteMapping("/api/v2/users/{id}/follow")
    fun unfollow(@PathVariable id: Long): ApiResponse<Any> {
        userService.unfollow(currentUserId(), id)
        return ApiResponse.ok()
    }

    @GetMapping("/api/v2/users/{id}/followers")
    fun followers(
        @PathVariable id: Long,
        @RequestParam(required = false) cursorId: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<UserV2FollowListResponseDto> =
        ApiResponse.ok(userService.followers(id, cursorId, size, optionalUserId()))

    @GetMapping("/api/v2/users/{id}/following")
    fun following(
        @PathVariable id: Long,
        @RequestParam(required = false) cursorId: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<UserV2FollowListResponseDto> =
        ApiResponse.ok(userService.following(id, cursorId, size, optionalUserId()))
}
