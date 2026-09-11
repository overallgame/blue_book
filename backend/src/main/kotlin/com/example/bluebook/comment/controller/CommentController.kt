package com.example.bluebook.comment.controller

import com.example.bluebook.comment.dto.CommentDto
import com.example.bluebook.comment.dto.CommentListDto
import com.example.bluebook.comment.dto.PostCommentRequestDto
import com.example.bluebook.comment.service.CommentService
import com.example.bluebook.common.ApiResponse
import com.example.bluebook.common.UnauthorizedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/comments")
class CommentController(private val commentService: CommentService) {

    private fun optionalUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)?.takeIf { it > 0 }

    /** 写接口专用：必须已登录。避免以 userId = 0 落库成"无作者评论" */
    private fun requireUserId(): Long = optionalUserId() ?: throw UnauthorizedException()

    @GetMapping
    fun getComments(
        @RequestParam videoId: Long,
        @RequestParam(required = false) cursorId: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<CommentListDto> =
        ApiResponse.ok(commentService.getRootComments(videoId, cursorId, size, optionalUserId()))

    @GetMapping("/{id}/replies")
    fun getReplies(
        @PathVariable id: Long,
        @RequestParam(required = false) cursorId: Long?,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<CommentListDto> =
        ApiResponse.ok(commentService.getReplies(id, cursorId, size, optionalUserId()))

    @PostMapping
    fun postComment(@RequestBody request: PostCommentRequestDto): ApiResponse<CommentDto> =
        ApiResponse.ok(commentService.postComment(requireUserId(), request))

    @DeleteMapping("/{id}")
    fun deleteComment(@PathVariable id: Long): ApiResponse<Any> {
        commentService.deleteComment(requireUserId(), id)
        return ApiResponse.ok()
    }

    @PostMapping("/{id}/like")
    fun likeComment(@PathVariable id: Long, @RequestParam liked: Boolean): ApiResponse<Any> {
        commentService.likeComment(requireUserId(), id, liked)
        return ApiResponse.ok()
    }
}
