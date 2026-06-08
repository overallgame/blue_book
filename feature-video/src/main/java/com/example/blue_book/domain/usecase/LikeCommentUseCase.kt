package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.CommentRepository
import javax.inject.Inject

class LikeCommentUseCase @Inject constructor(
    private val repository: CommentRepository
) {
    suspend operator fun invoke(commentId: Long, liked: Boolean): Result<Unit> {
        return repository.likeComment(commentId, liked)
    }
}
