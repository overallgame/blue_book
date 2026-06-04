package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.Comment
import com.example.blue_book.domain.repository.CommentRepository
import javax.inject.Inject

class ReplyCommentUseCase @Inject constructor(
    private val repository: CommentRepository
) {
    suspend operator fun invoke(
        parentId: Long,
        videoId: Long,
        content: String,
        replyToUserId: Long
    ): Result<Comment> {
        return repository.replyComment(parentId, videoId, content, replyToUserId)
    }
}
