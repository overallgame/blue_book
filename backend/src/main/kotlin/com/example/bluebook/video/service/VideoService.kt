package com.example.bluebook.video.service

import com.example.bluebook.auth.entity.User
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.BusinessException
import com.example.bluebook.common.VideoNotFoundException
import com.example.bluebook.interaction.repository.VideoCollectRepository
import com.example.bluebook.interaction.repository.VideoLikeRepository
import com.example.bluebook.notification.entity.NotifyType
import com.example.bluebook.notification.service.NotificationService
import com.example.bluebook.video.dto.*
import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.entity.VideoStatus
import com.example.bluebook.video.repository.VideoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class VideoService(
    private val videoRepository: VideoRepository,
    private val userRepository: UserRepository,
    private val likeRepository: VideoLikeRepository,
    private val collectRepository: VideoCollectRepository,
    private val redisTemplate: StringRedisTemplate,
    private val notificationService: NotificationService
) {
    fun feed(cursorId: Long?, size: Int, currentUserId: Long?): FeedResponseDto {
        val pageable = PageRequest.of(0, size)
        val videos = videoRepository.findFeedVideos(cursorId, pageable)
        val items = videos.map { v -> toDto(v, currentUserId) }
        return FeedResponseDto(items = items, nextCursorId = items.lastOrNull()?.videoId)
    }

    fun search(keyword: String, cursorId: Long?, size: Int, currentUserId: Long?): FeedResponseDto {
        val pageable = PageRequest.of(0, size)
        val videos = videoRepository.searchVideos(keyword, cursorId, pageable)
        val items = videos.map { v -> toDto(v, currentUserId) }
        return FeedResponseDto(items = items, nextCursorId = items.lastOrNull()?.videoId)
    }

    fun getVideoDto(videoId: Long, currentUserId: Long?): Video2Dto {
        val video = videoRepository.findByIdAndStatus(videoId, VideoStatus.PUBLISHED)
            ?: throw VideoNotFoundException()
        return toDto(video, currentUserId)
    }

    fun getPlayUrl(videoId: Long, cid: Long): String {
        val video = videoRepository.findByIdAndStatus(videoId, VideoStatus.PUBLISHED)
            ?: throw VideoNotFoundException()
        return video.hlsUrl ?: throw BusinessException(11003, "视频正在转码中，请稍后再试")
    }

    @Transactional
    fun publish(uploaderId: Long, request: PublishRequest): Video2Dto {
        val video = Video(
            uploaderId = uploaderId,
            title = request.title,
            description = request.description,
            originalUrl = request.filePath,
            transcodeStatus = com.example.bluebook.video.entity.TranscodeStatus.PENDING
        )
        videoRepository.save(video)
        // In production: send RabbitMQ message for transcoding
        // rabbitTemplate.convertAndSend("video.transcode", "", video.id)
        return toDto(video, uploaderId)
    }

    @Transactional
    fun likeVideo(userId: Long, videoId: Long, liked: Boolean) {
        val video = videoRepository.findByIdAndStatus(videoId, VideoStatus.PUBLISHED)
            ?: throw VideoNotFoundException()
        val exists = likeRepository.existsByUserIdAndVideoId(userId, videoId)
        if (liked && !exists) {
            likeRepository.save(com.example.bluebook.interaction.entity.VideoLike(userId = userId, videoId = videoId))
            videoRepository.incrementLikeCount(videoId, 1)
            if (userId != video.uploaderId) {
                notificationService.create(
                    receiverId = video.uploaderId, senderId = userId,
                    type = NotifyType.LIKE,
                    videoId = videoId,
                    content = "赞了你的视频"
                )
            }
        } else if (!liked && exists) {
            likeRepository.deleteByUserIdAndVideoId(userId, videoId)
            videoRepository.incrementLikeCount(videoId, -1)
        }
    }

    @Transactional
    fun collectVideo(userId: Long, videoId: Long, collected: Boolean) {
        val video = videoRepository.findByIdAndStatus(videoId, VideoStatus.PUBLISHED)
            ?: throw VideoNotFoundException()
        val exists = collectRepository.existsByUserIdAndVideoId(userId, videoId)
        if (collected && !exists) {
            collectRepository.save(com.example.bluebook.interaction.entity.VideoCollect(userId = userId, videoId = videoId))
            videoRepository.incrementCollectCount(videoId, 1)
            if (userId != video.uploaderId) {
                notificationService.create(
                    receiverId = video.uploaderId, senderId = userId,
                    type = NotifyType.LIKE,
                    videoId = videoId,
                    content = "收藏了你的视频"
                )
            }
        } else if (!collected && exists) {
            collectRepository.deleteByUserIdAndVideoId(userId, videoId)
            videoRepository.incrementCollectCount(videoId, -1)
        }
    }

    fun getTranscodeStatus(videoId: Long): String {
        val video = videoRepository.findById(videoId).orElseThrow { VideoNotFoundException() }
        return video.transcodeStatus.name
    }

    fun getLikedVideos(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): FeedResponseDto {
        val pageable = PageRequest.of(0, size)
        val likes = likeRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val videoIds = likes.map { it.videoId }
        val videos = videoRepository.findAllById(videoIds)
            .filter { it.status == VideoStatus.PUBLISHED }
        val items = videos.map { v -> toDto(v, currentUserId) }
        return FeedResponseDto(items = items, nextCursorId = items.lastOrNull()?.videoId)
    }

    fun getCollectedVideos(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): FeedResponseDto {
        val pageable = PageRequest.of(0, size)
        val collects = collectRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val videoIds = collects.map { it.videoId }
        val videos = videoRepository.findAllById(videoIds)
            .filter { it.status == VideoStatus.PUBLISHED }
        val items = videos.map { v -> toDto(v, currentUserId) }
        return FeedResponseDto(items = items, nextCursorId = items.lastOrNull()?.videoId)
    }

    fun getUserVideos(uploaderId: Long, cursorId: Long?, size: Int, currentUserId: Long?): FeedResponseDto {
        val pageable = PageRequest.of(0, size)
        val videos = videoRepository.findByUploaderIdAndStatus(uploaderId, VideoStatus.PUBLISHED, pageable)
        val items = videos.map { v -> toDto(v, currentUserId) }
        return FeedResponseDto(items = items, nextCursorId = items.lastOrNull()?.videoId)
    }

    private fun toDto(video: Video, currentUserId: Long?): Video2Dto {
        val uploader = userRepository.findById(video.uploaderId).orElse(null)
        val isLike = currentUserId?.let { likeRepository.existsByUserIdAndVideoId(it, video.id) } ?: false
        val isCollect = currentUserId?.let { collectRepository.existsByUserIdAndVideoId(it, video.id) } ?: false
        return Video2Dto(
            videoId = video.id, uploaderId = video.uploaderId,
            uploaderNickname = uploader?.nickname ?: "", uploaderAvatar = uploader?.avatarUrl ?: "",
            title = video.title ?: "", description = video.description ?: "", coverUrl = video.coverUrl ?: "",
            videoUrl = video.hlsUrl ?: video.originalUrl ?: "",
            likeCount = video.likeCount, collectCount = video.collectCount,
            viewCount = video.viewCount, commentCount = video.commentCount,
            isLike = isLike, isCollect = isCollect
        )
    }
}
