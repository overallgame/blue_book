package com.example.bluebook.user.repository

import com.example.bluebook.user.entity.UserFollow
import com.example.bluebook.user.entity.UserFollowId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface UserFollowRepository : JpaRepository<UserFollow, UserFollowId> {
    fun existsByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Boolean

    /**
     * 取消关注。用批量 DELETE 而不是派生删除 `deleteByFollowerIdAndFolloweeId`：
     * 派生删除先 getResultList() 再逐条 em.remove，返回的是**查到**的行数而非删除数，
     * 并发下两个请求都会认为删掉了一行，于是各发出一次计数递减。
     * 批量语句返回的是真实的受影响行数。
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM UserFollow uf WHERE uf.followerId = :followerId AND uf.followeeId = :followeeId")
    fun deleteFollow(followerId: Long, followeeId: Long): Int

    fun countByFollowerId(followerId: Long): Long
    fun countByFolloweeId(followeeId: Long): Long

    /** 关注列表：按 followeeId 游标降序分页（用户 id 唯一且稳定，保证不漏不重） */
    @Query("SELECT uf.followeeId FROM UserFollow uf WHERE uf.followerId = :followerId AND (:cursorId IS NULL OR uf.followeeId < :cursorId) ORDER BY uf.followeeId DESC")
    fun findFolloweeIdsByFollowerId(followerId: Long, cursorId: Long?, pageable: Pageable): List<Long>

    /** 粉丝列表：按 followerId 游标降序分页 */
    @Query("SELECT uf.followerId FROM UserFollow uf WHERE uf.followeeId = :followeeId AND (:cursorId IS NULL OR uf.followerId < :cursorId) ORDER BY uf.followerId DESC")
    fun findFollowerIdsByFolloweeId(followeeId: Long, cursorId: Long?, pageable: Pageable): List<Long>
}
