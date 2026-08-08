package com.example.bluebook.user.service

import com.example.bluebook.auth.entity.User
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.BusinessException
import com.example.bluebook.common.UnauthorizedException
import com.example.bluebook.user.dto.*
import com.example.bluebook.user.entity.UserFollow
import com.example.bluebook.user.repository.UserFollowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val followRepository: UserFollowRepository
) {
    fun me(userId: Long): UserV2MeDto {
        val user = findUser(userId)
        return toMeDto(user)
    }

    @Transactional
    fun updateField(userId: Long, field: String, value: String) {
        val user = findUser(userId)
        when (field) {
            "nickname" -> user.nickname = value
            "bio" -> user.bio = value
            "gender" -> user.gender = value
            "birthday" -> user.birthday = LocalDate.parse(value)
            "occupation" -> user.occupation = value
            "region" -> user.region = value
            "school" -> user.school = value
        }
        userRepository.save(user)
    }

    @Transactional
    fun updateAvatar(userId: Long, path: String) {
        val user = findUser(userId)
        user.avatarUrl = path
        userRepository.save(user)
    }

    @Transactional
    fun updateBackground(userId: Long, path: String) {
        val user = findUser(userId)
        user.backgroundUrl = path
        userRepository.save(user)
    }

    @Transactional
    fun updateMe(userId: Long, request: UserV2UpdateRequestDto): UserV2MeDto {
        val user = findUser(userId)
        request.nickname?.let { user.nickname = it }
        request.bio?.let { user.bio = it }
        request.gender?.let { user.gender = it }
        request.birthday?.let { user.birthday = LocalDate.parse(it) }
        request.occupation?.let { user.occupation = it }
        request.region?.let { user.region = it }
        request.school?.let { user.school = it }
        request.backgroundImage?.let { user.backgroundUrl = it }
        userRepository.save(user)
        return toMeDto(user)
    }

    fun profile(userId: Long, currentUserId: Long?): UserV2ProfileDto {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(11001, "用户不存在") }
        val isFollowed = currentUserId?.let {
            followRepository.existsByFollowerIdAndFolloweeId(it, userId)
        } ?: false
        return toProfileDto(user, isFollowed)
    }

    @Transactional
    fun follow(followerId: Long, followeeId: Long) {
        if (followerId == followeeId) throw BusinessException(14001, "不能关注自己")
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) return
        followRepository.save(UserFollow(followerId = followerId, followeeId = followeeId))
        val follower = findUser(followerId)
        val followee = findUser(followeeId)
        follower.followingCount++
        followee.followerCount++
        userRepository.save(follower)
        userRepository.save(followee)
    }

    @Transactional
    fun unfollow(followerId: Long, followeeId: Long) {
        val deleted = followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId)
        if (deleted > 0) {
            val follower = findUser(followerId)
            val followee = findUser(followeeId)
            follower.followingCount--
            followee.followerCount--
            userRepository.save(follower)
            userRepository.save(followee)
        }
    }

    fun followers(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): UserV2FollowListResponseDto {
        val allFollows = userRepository.findAll().filter { u ->
            followRepository.existsByFollowerIdAndFolloweeId(u.id, userId)
        }
        val items = allFollows.map { u ->
            val isFollowed = currentUserId?.let {
                followRepository.existsByFollowerIdAndFolloweeId(it, u.id)
            } ?: false
            toProfileDto(u, isFollowed)
        }
        return UserV2FollowListResponseDto(items = items, nextCursorId = items.lastOrNull()?.id)
    }

    fun following(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): UserV2FollowListResponseDto {
        val followeeIds = followRepository.findFolloweeIdsByFollowerId(userId)
        val users = userRepository.findAllById(followeeIds)
        val items = users.map { u ->
            val isFollowed = currentUserId?.let {
                followRepository.existsByFollowerIdAndFolloweeId(it, u.id)
            } ?: false
            toProfileDto(u, isFollowed)
        }
        return UserV2FollowListResponseDto(items = items, nextCursorId = items.lastOrNull()?.id)
    }

    private fun findUser(userId: Long) =
        userRepository.findById(userId).orElseThrow { UnauthorizedException() }

    private fun toMeDto(user: User) = UserV2MeDto(
        id = user.id,
        phone = user.phone.replaceRange(3, 7, "****"),
        nickname = user.nickname,
        avatar = user.avatarUrl,
        backgroundImage = user.backgroundUrl,
        bio = user.bio,
        gender = user.gender,
        birthday = user.birthday?.toString(),
        occupation = user.occupation,
        region = user.region,
        school = user.school,
        followerCount = user.followerCount,
        followingCount = user.followingCount
    )

    private fun toProfileDto(user: User, isFollowed: Boolean) = UserV2ProfileDto(
        id = user.id,
        nickname = user.nickname,
        avatar = user.avatarUrl,
        backgroundImage = user.backgroundUrl,
        bio = user.bio,
        gender = user.gender,
        birthday = user.birthday?.toString(),
        occupation = user.occupation,
        region = user.region,
        school = user.school,
        followerCount = user.followerCount,
        followingCount = user.followingCount,
        isFollowed = isFollowed
    )
}
