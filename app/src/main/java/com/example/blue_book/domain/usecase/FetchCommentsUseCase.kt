package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.Comment
import com.example.blue_book.domain.repository.CommentRepository
import javax.inject.Inject

class FetchCommentsUseCase @Inject constructor(
    private val repository: CommentRepository
) {
    suspend operator fun invoke(
        videoId: Long,
        cursorId: Long? = null,
        size: Int = 20
    ): Result<List<Comment>> {
        return repository.fetchRootComments(videoId, cursorId, size)
    }
}
