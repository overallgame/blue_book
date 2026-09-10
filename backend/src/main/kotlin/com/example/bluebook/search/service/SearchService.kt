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

    /**
     * 猜你想搜：无关键词返回热度 top N；
     * 有关键词则从热词榜中模糊匹配（按热度排序，含匹配词优先）
     */
    fun suggest(keyword: String?, limit: Int = 10): List<String> {
        val normalized = keyword?.trim().orEmpty()
        val all = getHotSearches(limit = 200)
        if (normalized.isEmpty()) return all.take(limit)
        val lower = normalized.lowercase()
        return all.filter { it.lowercase().contains(lower) }.take(limit)
    }
}
