package com.example.blue_book.data.mapper

import com.example.blue_book.di.NetworkModule
import com.example.blue_book.domain.model.Video
import com.example.blue_book.data.remote.video.dto2.Video2Dto

fun List<Video2Dto>.toDomainVideos(): List<Video> {
    val base = NetworkModule.BASE_URL.trimEnd('/')
    fun abs(url: String?): String {
        val u = url?.trim().orEmpty()
        if (u.isBlank()) return ""
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        return if (u.startsWith("/")) "$base$u" else "$base/$u"
    }
    return map { v ->
        Video(
            aid = v.videoId,
            cid = 0,
            like = v.likeCount.toInt(),
            image = abs(v.coverUrl),
            avatar = abs(v.uploaderAvatar),
            collection = v.collectCount.toInt(),
            nickname = v.uploaderNickname,
            description = v.title.ifBlank { v.description }
        )
    }
}
