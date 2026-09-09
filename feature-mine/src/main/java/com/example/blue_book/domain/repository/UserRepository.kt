package com.example.blue_book.domain.repository

import com.example.blue_book.data.UserAccount
import com.example.blue_book.domain.model.FollowListPage

interface UserRepository {

	suspend fun getUserProfile(phone: String): Result<UserAccount>

	suspend fun updateUserProfile(account: UserAccount): Result<Unit>

	suspend fun currentUserPhone(): String?

	suspend fun updateNickname(phone: String, nickname: String): Result<Unit>

	suspend fun updateBio(phone: String, bio: String): Result<Unit>

	suspend fun updateGender(phone: String, gender: String): Result<Unit>

	suspend fun updateBirthday(phone: String, birthday: String): Result<Unit>

	suspend fun updateOccupation(phone: String, occupation: String): Result<Unit>

	suspend fun updateRegion(phone: String, region: String): Result<Unit>

	suspend fun updateSchool(phone: String, school: String): Result<Unit>

	suspend fun uploadAvatarFile(localUri: String): Result<String>

	suspend fun uploadBackgroundFile(localUri: String): Result<String>

	/** 他人主页信息（含 isFollowed 关注态） */
	suspend fun fetchUserProfile(userId: Long): Result<UserAccount>

	/** 关注 / 取关 */
	suspend fun followUser(userId: Long): Result<Unit>

	suspend fun unfollowUser(userId: Long): Result<Unit>

	/** 关注/粉丝列表（游标分页） */
	suspend fun fetchFollowing(userId: Long, cursorId: Long?, size: Int): Result<FollowListPage>

	suspend fun fetchFollowers(userId: Long, cursorId: Long?, size: Int): Result<FollowListPage>
}
