package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject

class FetchVideosByKeywordUseCase @Inject constructor(
	private val repository: VideoRepository
) {

	suspend operator fun invoke(keyword: String): Result<List<Video>> {
		return repository.fetchByKeyword(keyword)
	}
}


