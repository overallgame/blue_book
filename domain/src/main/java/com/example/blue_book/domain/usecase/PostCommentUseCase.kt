package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.Comment
import com.example.blue_book.domain.repository.CommentRepository
import javax.inject.Inject

class PostCommentUseCase @Inject constructor(
    private val repository: CommentRepository
) {
    suspend operator fun invoke(videoId: Long, content: String): Result<Comment> {
        return repository.postComment(videoId, content)
    }
}
