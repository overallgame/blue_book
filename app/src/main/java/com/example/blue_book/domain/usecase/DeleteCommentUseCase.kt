package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.CommentRepository
import javax.inject.Inject

class DeleteCommentUseCase @Inject constructor(
    private val repository: CommentRepository
) {
    suspend operator fun invoke(commentId: Long): Result<Unit> {
        return repository.deleteComment(commentId)
    }
}
