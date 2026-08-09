package com.example.bluebook.user.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.file.service.FileService
import com.example.bluebook.user.dto.*
import com.example.bluebook.user.service.UserService
import com.example.bluebook.video.dto.FeedResponseDto
import com.example.bluebook.video.service.VideoService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
class UserController(
    private val userService: UserService,
    private val videoService: VideoService,
    private val fileService: FileService
) {
    private fun currentUserId(): Long =
        SecurityContextHolder.getContext().authentication?.principal as? Long ?: 0

    private fun optionalUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)?.takeIf { it > 0 }

    // ========== 当前用户信息 ==========

    @GetMapping("/api/v2/me")
    fun me(): ApiResponse<UserV2MeDto> = ApiResponse.ok(userService.me(currentUserId()))

    // ========== 按字段独立编辑 ==========

    @PutMapping("/api/v2/me/nickname")
    fun updateNickname(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "nickname", body["nickname"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    @PutMapping("/api/v2/me/bio")
    fun updateBio(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "bio", body["bio"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    @PutMapping("/api/v2/me/gender")
    fun updateGender(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "gender", body["gender"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    @PutMapping("/api/v2/me/birthday")
    fun updateBirthday(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "birthday", body["birthday"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    @PutMapping("/api/v2/me/occupation")
    fun updateOccupation(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "occupation", body["occupation"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    @PutMapping("/api/v2/me/region")
    fun updateRegion(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "region", body["region"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    @PutMapping("/api/v2/me/school")
    fun updateSchool(@RequestBody body: Map<String, String>): ApiResponse<UserV2MeDto> {
        userService.updateField(currentUserId(), "school", body["school"] ?: "")
        return ApiResponse.ok(userService.me(currentUserId()))
    }

    // ========== 头像上传 ==========

    @PostMapping("/api/v2/me/avatar")
    fun uploadAvatar(@RequestParam("avatar") file: MultipartFile): ApiResponse<UserV2AvatarUploadResponseDto> {
        val path = fileService.uploadImage(file)
        userService.updateAvatar(currentUserId(), path)
        return ApiResponse.ok(UserV2AvatarUploadResponseDto(path))
    }

    // ========== 背景图上传（与头像上传一致） ==========

    @PostMapping("/api/v2/me/background")
    fun uploadBackground(@RequestParam("background") file: MultipartFile): ApiResponse<UserV2AvatarUploadResponseDto> {
        val path = fileService.uploadImage(file)
        userService.updateBackground(currentUserId(), path)
        return ApiResponse.ok(UserV2AvatarUploadResponseDto(path))
    }

    // ========== 批量编辑（保留兼容） ==========

    @PutMapping("/api/v2/me")
    fun updateMe(@RequestBody request: UserV2UpdateRequestDto): ApiResponse<UserV2MeDto> =
        ApiResponse.ok(userService.updateMe(currentUserId(), request))

    // ========== 他人主页 ==========

    @GetMapping("/api/v2/users/{id}")
    fun profile(@PathVariable id: Long): ApiResponse<UserV2ProfileDto> =
        ApiResponse.ok(userService.profile(id, optionalUserId()))

    @GetMapping("/api/v2/users/{id}/videos")
    fun userVideos(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) cursorId: Long?
    ): ApiResponse<FeedResponseDto> =
        ApiResponse.ok(videoService.getUserVideos(id, cursorId, size, optionalUserId()))

    // ========== 关注 ==========

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
