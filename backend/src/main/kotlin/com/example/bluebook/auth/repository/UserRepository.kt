package com.example.bluebook.auth.repository

import com.example.bluebook.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByPhone(phone: String): Optional<User>
    fun existsByPhone(phone: String): Boolean

    // 关注数走原子自增：原来的读-改-写（load → ++ → save）在并发下会丢失更新，
    // 而 User 上没有 @Version，乐观锁也帮不上。clearAutomatically 是必需的：
    // 批量更新会绕过持久化上下文，若同一事务内之后还读该实体，会拿到旧值。
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount + 1 WHERE u.id = :id")
    fun incrementFollowingCount(id: Long)

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount + 1 WHERE u.id = :id")
    fun incrementFollowerCount(id: Long)

    // 递减带下限保护：并发的取消关注可能各自算出「删掉了一行」而发出两次 -1，
    // WHERE 把多余的递减挡掉，计数不会变成负数（返回 0 表示被拦下）。
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount - 1 WHERE u.id = :id AND u.followingCount > 0")
    fun decrementFollowingCount(id: Long): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount - 1 WHERE u.id = :id AND u.followerCount > 0")
    fun decrementFollowerCount(id: Long): Int

    /**
     * "小红书号"懒生成写回。用定向 UPDATE 而不是 `save(user)`：后者整行 merge，
     * 而这条路径被 allowAll 的 `GET /api/v2/users/{id}` 触达，
     * 一次未认证的读会把读取时的整行快照写回，覆盖并发的资料编辑。
     * `xhsId IS NULL` 让并发调用只有一个真正写入。
     */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.xhsId = :xhsId WHERE u.id = :id AND u.xhsId IS NULL")
    fun assignXhsIdIfAbsent(id: Long, xhsId: String): Int

    // 每日对账：以 user_follow 为唯一事实来源重算两张计数。
    // WHERE 只匹配确实漂移的行，于是返回的行数就是漂移行数（可用于告警），
    // 且不会给全表加锁。SET 列不加别名限定，`user` 需要反引号（保留字风格的表名）。
    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE `user` u SET follower_count = (SELECT COUNT(*) FROM user_follow f WHERE f.followee_id = u.id)
            WHERE follower_count <> (SELECT COUNT(*) FROM user_follow f WHERE f.followee_id = u.id)
        """,
        nativeQuery = true
    )
    fun reconcileFollowerCounts(): Int

    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE `user` u SET following_count = (SELECT COUNT(*) FROM user_follow f WHERE f.follower_id = u.id)
            WHERE following_count <> (SELECT COUNT(*) FROM user_follow f WHERE f.follower_id = u.id)
        """,
        nativeQuery = true
    )
    fun reconcileFollowingCounts(): Int
}
