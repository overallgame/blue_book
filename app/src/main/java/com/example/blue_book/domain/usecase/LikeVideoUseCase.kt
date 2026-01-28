package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject

class LikeVideoUseCase @Inject constructor(
    private val repository: VideoRepository
) {

    suspend operator fun invoke(aid: Long, cid: Long, liked: Boolean): Result<Unit> {
        return repository.likeVideo(aid, cid, liked)
    }
}
