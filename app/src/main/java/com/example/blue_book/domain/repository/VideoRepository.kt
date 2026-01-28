package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.Video

interface VideoRepository {

	suspend fun fetchRandom(): Result<List<Video>>

	suspend fun fetchByKeyword(keyword: String): Result<List<Video>>

    suspend fun fetchPlayUrl(aid: Long, cid: Long): Result<String>

	suspend fun likeVideo(aid: Long, cid: Long, liked: Boolean): Result<Unit>
}
