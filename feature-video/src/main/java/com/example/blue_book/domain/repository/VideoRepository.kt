package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.Video

interface VideoRepository {

	suspend fun fetchRandom(cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchByKeyword(keyword: String, cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchPlayUrl(aid: Long, cid: Long): Result<String>

	suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit>

	suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit>

	suspend fun fetchLikedVideos(cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchCollectedVideos(cursorId: Long?, size: Int?): Result<List<Video>>

	suspend fun fetchUserVideos(userId: Long, cursorId: Long?, size: Int?): Result<List<Video>>
}
