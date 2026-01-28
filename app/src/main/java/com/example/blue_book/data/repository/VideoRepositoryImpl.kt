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

    override suspend fun fetchRandom(): Result<List<Video>> {
        val result = remote.feed(cursorId = null, size = 10)
        return result.map { it.items.toDomainVideos() }
    }

    override suspend fun fetchByKeyword(keyword: String): Result<List<Video>> {
        val trimmed = keyword.trim()
        val result = remote.feed(cursorId = null, size = 30)
        return result.map { resp ->
            val items = if (trimmed.isBlank()) {
                resp.items
            } else {
                resp.items.filter { v ->
                    v.title.contains(trimmed, ignoreCase = true)
                        || v.description.contains(trimmed, ignoreCase = true)
                        || v.uploaderNickname.contains(trimmed, ignoreCase = true)
                }
            }
            items.toDomainVideos()
        }
    }

    override suspend fun fetchPlayUrl(aid: Long, cid: Long): Result<String> {
        val base = NetworkModule.BASE_URL.trimEnd('/')
        return Result.success("$base/api/v2/videos/$aid/stream")
    }

    override suspend fun likeVideo(aid: Long, cid: Long, liked: Boolean): Result<Unit> {
        return remote.likeVideo(aid, liked)
    }
}