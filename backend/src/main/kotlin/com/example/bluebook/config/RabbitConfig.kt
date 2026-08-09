package com.example.bluebook.config

import org.springframework.amqp.core.Queue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class RabbitConfig {
    @Bean
    fun transcodeQueue(): Queue = Queue("video.transcode", true)
}
