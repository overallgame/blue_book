package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject

class FetchRandomVideosUseCase @Inject constructor(
	private val repository: VideoRepository
) {

	suspend operator fun invoke(): Result<List<Video>> {
		return repository.fetchRandom()
	}
}


