package com.example.bluebook.user.repository

import com.example.bluebook.user.entity.UserFollow
import com.example.bluebook.user.entity.UserFollowId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserFollowRepository : JpaRepository<UserFollow, UserFollowId> {
    fun existsByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Boolean
    fun deleteByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Int
    fun countByFollowerId(followerId: Long): Long
    fun countByFolloweeId(followeeId: Long): Long

    /** 关注列表：按 followeeId 游标降序分页（用户 id 唯一且稳定，保证不漏不重） */
    @Query("SELECT uf.followeeId FROM UserFollow uf WHERE uf.followerId = :followerId AND (:cursorId IS NULL OR uf.followeeId < :cursorId) ORDER BY uf.followeeId DESC")
    fun findFolloweeIdsByFollowerId(followerId: Long, cursorId: Long?, pageable: Pageable): List<Long>

    /** 粉丝列表：按 followerId 游标降序分页 */
    @Query("SELECT uf.followerId FROM UserFollow uf WHERE uf.followeeId = :followeeId AND (:cursorId IS NULL OR uf.followerId < :cursorId) ORDER BY uf.followerId DESC")
    fun findFollowerIdsByFolloweeId(followeeId: Long, cursorId: Long?, pageable: Pageable): List<Long>
}
