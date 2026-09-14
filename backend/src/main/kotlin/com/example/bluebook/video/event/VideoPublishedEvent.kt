package com.example.bluebook.video.event

/**
 * 视频已发布。由 [com.example.bluebook.video.service.VideoService.publish] 在事务内发布，
 * 由监听器在**事务提交后**才投递到 MQ——否则消费者可能先于事务提交读到任务，
 * 查不到视频行便会丢弃消息，视频永久停在 PENDING。
 */
data class VideoPublishedEvent(val videoId: Long)
