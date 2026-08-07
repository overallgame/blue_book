package com.example.bluebook.user.repository

import com.example.bluebook.user.entity.UserFollow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserFollowRepository : JpaRepository<UserFollow, Long> {
    fun existsByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Boolean
    fun deleteByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Int
    fun countByFollowerId(followerId: Long): Long
    fun countByFolloweeId(followeeId: Long): Long

    @Query("SELECT uf.followeeId FROM UserFollow uf WHERE uf.followerId = :followerId")
    fun findFolloweeIdsByFollowerId(followerId: Long): List<Long>
}
