package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject

class CollectVideoUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(aid: Long, collected: Boolean): Result<Unit> {
        return repository.collectVideo(aid, collected)
    }
}
