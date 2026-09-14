package com.example.bluebook.user.service

import com.example.bluebook.auth.entity.User
import com.example.bluebook.common.assetUrl
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.BusinessException
import com.example.bluebook.common.UnauthorizedException
import com.example.bluebook.user.dto.*
import com.example.bluebook.user.entity.UserFollow
import com.example.bluebook.user.repository.UserFollowRepository
import com.example.bluebook.video.repository.VideoRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val followRepository: UserFollowRepository,
    private val videoRepository: VideoRepository
) {
    fun me(userId: Long): UserV2MeDto {
        val user = findUser(userId)
        ensureXhsId(user)
        val (liked, collected) = interactionSum(userId)
        return toMeDto(user, liked, collected)
    }

    /**
     * "小红书号"懒生成：SHA-256(id + 固定盐) 派生 10 位 base36，
     * 不可逆、不含手机号信息；SHA-256 前 10 位碰撞概率 1/36^10，实际可忽略
     */
    private fun ensureXhsId(user: User): String {
        user.xhsId?.takeIf { it.isNotBlank() }?.let { return it }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("bluebook:xhs:${user.id}:$XHS_SALT".toByteArray())
        var value = 0L
        for (b in digest.take(7)) {
            value = (value shl 8) or (b.toLong() and 0xFF)
        }
        if (value == 0L) value = 1L
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val id = buildString {
            var v = value
            repeat(10) {
                append(chars[(v % 36).toInt()])
                v /= 36
            }
        }
        // 定向写回：整行 save 会把读取时的快照覆盖回去，而本方法被允许匿名访问的
        // GET /api/v2/users/{id} 触达，会回退并发的资料编辑
        userRepository.assignXhsIdIfAbsent(user.id, id)
        user.xhsId = id
        return id
    }

    /** 获赞与收藏合计：[0]=点赞合计，[1]=收藏合计 */
    private fun interactionSum(uploaderId: Long): Pair<Long, Long> {
        val row = videoRepository.sumInteractionsByUploader(uploaderId).firstOrNull() as? Array<*>
        val liked = (row?.getOrNull(0) as? Number)?.toLong() ?: 0L
        val collected = (row?.getOrNull(1) as? Number)?.toLong() ?: 0L
        return liked to collected
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
        ensureXhsId(user)
        val (liked, collected) = interactionSum(userId)
        return toMeDto(user, liked, collected)
    }

    fun profile(userId: Long, currentUserId: Long?): UserV2ProfileDto {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(11001, "用户不存在") }
        ensureXhsId(user)
        val isFollowed = currentUserId?.let {
            followRepository.existsByFollowerIdAndFolloweeId(it, userId)
        } ?: false
        val (liked, collected) = interactionSum(userId)
        return toProfileDto(user, isFollowed, liked, collected)
    }

    @Transactional
    fun follow(followerId: Long, followeeId: Long) {
        if (followerId == followeeId) throw BusinessException(14001, "不能关注自己")
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) return
        // 保留存在性校验：关注不存在的用户应当回滚（与改动前一致）
        findUser(followeeId)
        followRepository.save(UserFollow(followerId = followerId, followeeId = followeeId))
        // 原子自增，避免并发关注时丢失更新
        userRepository.incrementFollowingCount(followerId)
        userRepository.incrementFollowerCount(followeeId)
    }

    @Transactional
    fun unfollow(followerId: Long, followeeId: Long) {
        // 批量 DELETE 返回真实受影响行数（派生删除返回的是「查到」的行数，并发下会重复递减）；
        // 递减本身带下限保护，见 UserRepository
        if (followRepository.deleteFollow(followerId, followeeId) > 0) {
            userRepository.decrementFollowingCount(followerId)
            userRepository.decrementFollowerCount(followeeId)
        }
    }

    fun followers(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): UserV2FollowListResponseDto {
        val pageable = PageRequest.of(0, size)
        val followerIds = followRepository.findFollowerIdsByFolloweeId(userId, cursorId, pageable)
        val userMap = userRepository.findAllById(followerIds).associateBy { it.id }
        val items = followerIds.mapNotNull { id ->
            val u = userMap[id] ?: return@mapNotNull null
            val isFollowed = currentUserId?.let {
                followRepository.existsByFollowerIdAndFolloweeId(it, u.id)
            } ?: false
            toProfileDto(u, isFollowed)
        }
        return UserV2FollowListResponseDto(
            items = items,
            nextCursorId = items.lastOrNull()?.id,
            hasMore = followerIds.size == size
        )
    }

    fun following(userId: Long, cursorId: Long?, size: Int, currentUserId: Long?): UserV2FollowListResponseDto {
        val pageable = PageRequest.of(0, size)
        val followeeIds = followRepository.findFolloweeIdsByFollowerId(userId, cursorId, pageable)
        val userMap = userRepository.findAllById(followeeIds).associateBy { it.id }
        val items = followeeIds.mapNotNull { id ->
            val u = userMap[id] ?: return@mapNotNull null
            val isFollowed = currentUserId?.let {
                followRepository.existsByFollowerIdAndFolloweeId(it, u.id)
            } ?: false
            toProfileDto(u, isFollowed)
        }
        return UserV2FollowListResponseDto(
            items = items,
            nextCursorId = items.lastOrNull()?.id,
            hasMore = followeeIds.size == size
        )
    }

    private fun findUser(userId: Long) =
        userRepository.findById(userId).orElseThrow { UnauthorizedException() }

    private companion object {
        /** 小红书号派生盐（更换会使所有号变化，勿轻易修改） */
        const val XHS_SALT = "bluebook-xhs-v1"
    }

    private fun toMeDto(user: User, liked: Long = 0, collected: Long = 0) = UserV2MeDto(
        id = user.id,
        phone = user.phone.replaceRange(3, 7, "****"),
        xhsId = user.xhsId,
        nickname = user.nickname,
        avatar = assetUrl(user.avatarUrl, "upload/images"),
        backgroundImage = assetUrl(user.backgroundUrl, "upload/images"),
        bio = user.bio,
        gender = user.gender,
        birthday = user.birthday?.toString(),
        occupation = user.occupation,
        region = user.region,
        school = user.school,
        followerCount = user.followerCount,
        followingCount = user.followingCount,
        likedCount = liked,
        collectedCount = collected
    )

    private fun toProfileDto(
        user: User,
        isFollowed: Boolean,
        liked: Long = 0,
        collected: Long = 0
    ) = UserV2ProfileDto(
        id = user.id,
        xhsId = user.xhsId,
        nickname = user.nickname,
        avatar = assetUrl(user.avatarUrl, "upload/images"),
        backgroundImage = assetUrl(user.backgroundUrl, "upload/images"),
        bio = user.bio,
        gender = user.gender,
        birthday = user.birthday?.toString(),
        occupation = user.occupation,
        region = user.region,
        school = user.school,
        followerCount = user.followerCount,
        followingCount = user.followingCount,
        isFollowed = isFollowed,
        likedCount = liked,
        collectedCount = collected
    )
}
