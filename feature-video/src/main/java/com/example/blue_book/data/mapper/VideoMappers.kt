package com.example.blue_book.data.mapper

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.domain.model.Video
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import com.example.blue_book.util.absoluteUrl

fun List<Video2Dto>.toDomainVideos(): List<Video> {
	fun abs(url: String?): String = absoluteUrl(ApiGateway.BASE_URL, url) ?: ""
	return map { v ->
		Video(
			aid = v.videoId,
			cid = 0,
			like = v.likeCount.toInt(),
			image = abs(v.coverUrl),
			avatar = abs(v.uploaderAvatar),
			collection = v.collectCount.toInt(),
			nickname = v.uploaderNickname,
			description = v.title.ifBlank { v.description },
			isLike = v.isLike,
			isCollect = v.isCollect,
			isFollowed = v.isFollowed,
			playUrl = abs(v.videoUrl),
			commentCount = v.commentCount.toInt(),
			uploaderId = v.uploaderId
		)
	}
}
