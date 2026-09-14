package com.example.bluebook.video.service

import com.example.bluebook.video.event.VideoPublishedEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 转码任务投递器。
 *
 * 必须用 `@TransactionalEventListener(AFTER_COMMIT)`：此前是在 publish 事务内直接
 * `convertAndSend`，消费者可能在事务提交前就取到消息，`findById` 查不到视频行，
 * 只重试一次便把消息丢弃，视频永久停在 PENDING（feed 只显示 DONE，用户看到的是
 * 「上传成功却永远不出现」）。提交后再投递可以从根上消除这个竞态，
 * 也让 TranscodeConsumer 不必再用 Thread.sleep 拖延。
 */
@Component
class TranscodeTaskPublisher(
    private val rabbitTemplate: RabbitTemplate? = null
) {
    private val log = LoggerFactory.getLogger(TranscodeTaskPublisher::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onVideoPublished(event: VideoPublishedEvent) {
        val rabbit = rabbitTemplate
        if (rabbit == null) {
            log.warn("RabbitTemplate 不可用，转码任务未投递: videoId={}（定时兜底会重投）", event.videoId)
            return
        }
        runCatching { rabbit.convertAndSend("video.transcode", event.videoId) }
            .onFailure { log.error("转码任务投递失败: videoId={}（定时兜底会重投）", event.videoId, it) }
    }
}
