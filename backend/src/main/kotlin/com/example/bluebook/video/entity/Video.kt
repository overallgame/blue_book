package com.example.bluebook.video.entity

import com.example.bluebook.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "video", indexes = [
    Index(name = "idx_uploader_created", columnList = "uploader_id,created_at"),
    Index(name = "idx_feed", columnList = "status,created_at")
])
class Video(
    @Column(name = "uploader_id", nullable = false)
    var uploaderId: Long,

    @Column(name = "title", length = 200)
    var title: String? = null,

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Column(name = "cover_url", length = 500)
    var coverUrl: String? = null,

    @Column(name = "original_url", length = 500)
    var originalUrl: String? = null,

    @Column(name = "hls_url", length = 500)
    var hlsUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "transcode_status", nullable = false, length = 20)
    var transcodeStatus: TranscodeStatus = TranscodeStatus.PENDING,

    /**
     * 转码专用时间戳，只由转码路径写入（发布、开始、完成、兜底重投）。
     * 不能复用 BaseEntity.updatedAt：点赞/播放/评论都会顶掉 updatedAt，
     * 卡死的转码任务就会永远不满足"超过阈值"，兜底形同失效。
     */
    @Column(name = "transcode_updated_at")
    var transcodeUpdatedAt: LocalDateTime? = LocalDateTime.now(),

    @Column(name = "duration")
    var duration: Int? = null,

    @Column(name = "width")
    var width: Int? = null,

    @Column(name = "height")
    var height: Int? = null,

    @Column(name = "file_size")
    var fileSize: Long? = null,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "collect_count", nullable = false)
    var collectCount: Long = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Long = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,

    /** 发布时定位城市（"本地"流按此过滤，未授权定位则为空） */
    @Column(name = "region", length = 100)
    var region: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: VideoStatus = VideoStatus.PUBLISHED
) : BaseEntity()

enum class TranscodeStatus { PENDING, PROCESSING, DONE, FAILED }
enum class VideoStatus { PUBLISHED, DELETED, REVIEWING }
