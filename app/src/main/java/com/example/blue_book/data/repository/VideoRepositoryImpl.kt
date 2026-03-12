package com.example.blue_book.data.repository

import com.example.blue_book.di.NetworkModule
import com.example.blue_book.data.mapper.toDomainVideos
import com.example.blue_book.data.remote.video.VideoRemoteDataSource
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val remote: VideoRemoteDataSource
): VideoRepository {

    override suspend fun fetchRandom(cursorId: Long?, size: Int?): Result<List<Video>> {
        val result = remote.feed(cursorId, size)
        return result.map { it.items.toDomainVideos() }
    }

    override suspend fun fetchByKeyword(keyword: String, cursorId: Long?, size: Int?): Result<List<Video>> {
        val result = remote.searchVideos(keyword, cursorId, size)
        return result.map { it.items.toDomainVideos() }
    }

    override suspend fun fetchPlayUrl(aid: Long, cid: Long): Result<String> {
        return remote.getPlayUrl(aid, cid)
    }

    override suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit> {
        return remote.likeVideo(aid, liked)
    }

    override suspend fun collectVideo(aid: Long, collected: Boolean): Result<Unit> {
        return remote.collectVideo(aid, collected)
    }
}