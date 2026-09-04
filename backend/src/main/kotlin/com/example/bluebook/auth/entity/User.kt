package com.example.bluebook.auth.entity

import com.example.bluebook.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "`user`")
class User(
    @Column(name = "phone", nullable = false, unique = true, length = 20)
    var phone: String,

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null,

    @Column(name = "background_url", length = 500)
    var backgroundUrl: String? = null,

    @Column(name = "bio", length = 200)
    var bio: String? = null,

    @Column(name = "gender", length = 10)
    var gender: String? = null,

    @Column(name = "birthday")
    var birthday: LocalDate? = null,

    @Column(name = "occupation", length = 100)
    var occupation: String? = null,

    @Column(name = "region", length = 100)
    var region: String? = null,

    @Column(name = "school", length = 100)
    var school: String? = null,

    @Column(name = "follower_count", nullable = false)
    var followerCount: Long = 0,

    @Column(name = "following_count", nullable = false)
    var followingCount: Long = 0
) : BaseEntity()
