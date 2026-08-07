package com.example.bluebook.search.service

import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.interaction.repository.VideoCollectRepository
import com.example.bluebook.interaction.repository.VideoLikeRepository
import com.example.bluebook.video.dto.FeedResponseDto
import com.example.bluebook.video.dto.Video2Dto
import com.example.bluebook.video.entity.VideoStatus
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val userRepository: UserRepository,
    private val likeRepository: VideoLikeRepository,
    private val collectRepository: VideoCollectRepository,
    private val redisTemplate: StringRedisTemplate
) {
    // Search with simple DB LIKE query fallback (ES not configured for MVP)
    // In production, replace with Elasticsearch multi_match query
    // This is a placeholder - actual search needs a native SQL query or ES

    fun recordSearchKeyword(keyword: String) {
        // Increment hot search word in Redis Sorted Set
        redisTemplate.opsForZSet().incrementScore("hot:search", keyword, 1.0)
    }

    fun getHotSearches(limit: Int = 20): List<String> {
        val results = redisTemplate.opsForZSet().reverseRangeWithScores("hot:search", 0, limit.toLong() - 1)
        return results?.map { it.value ?: "" }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}
