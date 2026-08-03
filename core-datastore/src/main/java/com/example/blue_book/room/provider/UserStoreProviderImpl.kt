package com.example.blue_book.room.provider

import com.example.blue_book.data.UserAccount
import com.example.blue_book.provider.IUserStore
import com.example.blue_book.room.dao.UserDao
import com.example.blue_book.room.entity.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserStoreProviderImpl @Inject constructor(
	private val userDao: UserDao
) : IUserStore {

	override suspend fun saveUser(account: UserAccount) {
		userDao.insert(account.toEntity())
	}

	override suspend fun updateUser(account: UserAccount) {
		userDao.update(account.toEntity())
	}

	override suspend fun getUserByPhone(phone: String): UserAccount? {
		return userDao.getCurrentUser(phone)?.toDomain()
	}

	override suspend fun deleteUserByPhone(phone: String) {
		userDao.getCurrentUser(phone)?.let { userDao.delete(it) }
	}

	override suspend fun followUser(phone: String, follow: Boolean): Result<Unit> {
		val entity = userDao.getCurrentUser(phone) ?: return Result.failure(Exception("用户不存在"))
		val updated = entity.copy(isFollowed = follow)
		userDao.update(updated)
		return Result.success(Unit)
	}

	override suspend fun isFollowing(phone: String): Boolean {
		return userDao.getCurrentUser(phone)?.isFollowed ?: false
	}

	private fun UserEntity.toDomain() = UserAccount(
		phone = phone, avatar = avatar, nickname = nickname,
		password = password, introduction = introduction,
		sex = sex, birthday = birthday, career = career,
		region = region, school = school, background = background,
		isFollowed = isFollowed
	)

	private fun UserAccount.toEntity() = UserEntity(
		phone = phone, avatar = avatar, nickname = nickname,
		password = password ?: "", introduction = introduction,
		sex = sex, birthday = birthday, career = career,
		region = region, school = school, background = background,
		authToken = null, refreshToken = null, last_Login = System.currentTimeMillis(),
		isFollowed = isFollowed
	)
}
