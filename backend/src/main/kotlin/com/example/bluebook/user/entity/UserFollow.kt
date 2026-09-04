package com.example.bluebook.user.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "user_follow")
@IdClass(UserFollowId::class)
class UserFollow(
    @Id
    @Column(name = "follower_id", nullable = false)
    var followerId: Long,

    @Id
    @Column(name = "followee_id", nullable = false)
    var followeeId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

class UserFollowId : Serializable {
    var followerId: Long = 0
    var followeeId: Long = 0
    override fun equals(other: Any?) = other is UserFollowId && other.followerId == followerId && other.followeeId == followeeId
    override fun hashCode() = 31 * followerId.hashCode() + followeeId.hashCode()
}
