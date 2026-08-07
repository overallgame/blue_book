package com.example.bluebook

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class BlueBookApplicationTests {

    @MockBean
    lateinit var stringRedisTemplate: StringRedisTemplate

    @MockBean
    lateinit var rabbitTemplate: RabbitTemplate

    @Test
    fun contextLoads() {
        // Spring context load smoke test — verifies all beans can be wired
    }
}
