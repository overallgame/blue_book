package com.example.bluebook.auth.repository

import com.example.bluebook.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByPhone(phone: String): Optional<User>
    fun existsByPhone(phone: String): Boolean

    // 关注数走原子自增：原来的读-改-写（load → ++ → save）在并发下会丢失更新，
    // 而 User 上没有 @Version，乐观锁也帮不上。clearAutomatically 是必需的：
    // 批量更新会绕过持久化上下文，若同一事务内之后还读该实体，会拿到旧值。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount + :delta WHERE u.id = :id")
    fun incrementFollowingCount(id: Long, delta: Long)

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount + :delta WHERE u.id = :id")
    fun incrementFollowerCount(id: Long, delta: Long)

    /** 每日对账：以 user_follow 为唯一事实来源重算两张计数（返回实际改动的行数） */
    @Modifying
    @Query(
        value = "UPDATE `user` u SET u.follower_count = (SELECT COUNT(*) FROM user_follow f WHERE f.followee_id = u.id)",
        nativeQuery = true
    )
    fun reconcileFollowerCounts(): Int

    @Modifying
    @Query(
        value = "UPDATE `user` u SET u.following_count = (SELECT COUNT(*) FROM user_follow f WHERE f.follower_id = u.id)",
        nativeQuery = true
    )
    fun reconcileFollowingCounts(): Int
}
