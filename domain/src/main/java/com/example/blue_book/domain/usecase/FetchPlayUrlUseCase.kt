package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject

class FetchPlayUrlUseCase @Inject constructor(
	private val videoRepository: VideoRepository
) {
	suspend operator fun invoke(aid: Long, cid: Long): Result<String> {
		return videoRepository.fetchPlayUrl(aid, cid)
	}
}
