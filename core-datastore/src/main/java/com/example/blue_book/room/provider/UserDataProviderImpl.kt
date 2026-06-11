package com.example.blue_book.room.provider

import com.example.blue_book.data.UserAccount
import com.example.blue_book.provider.IUserDataProvider
import com.example.blue_book.room.entity.UserEntity
import com.example.blue_book.room.user.UserLocalDataResource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IUserDataProvider 实现，位于 core-datastore。
 * 内部使用 Room Entity，对外暴露 UserAccount。
 */
@Singleton
class UserDataProviderImpl @Inject constructor(
    private val userLocalDataResource: UserLocalDataResource
) : IUserDataProvider {

    override suspend fun saveUser(user: UserAccount) {
        userLocalDataResource.saveUser(user.toEntity())
    }

    override suspend fun updateUser(user: UserAccount) {
        userLocalDataResource.updateUser(user.toEntity())
    }

    override suspend fun getUserByPhone(phone: String): UserAccount? {
        return userLocalDataResource.getCurrentUser(phone)?.toDomain()
    }

    override suspend fun clearUser() {
        userLocalDataResource.clearUser()
    }

    override fun getCurrentUserPhone(): String? {
        return userLocalDataResource.getCurrentUserPhone()
    }

    companion object {
        @Volatile
        var instance: IUserDataProvider? = null
    }

    init {
        instance = this
    }

    private fun UserEntity.toDomain(): UserAccount = UserAccount(
        phone = phone,
        avatar = avatar,
        nickname = nickname,
        password = password,
        introduction = introduction,
        sex = sex,
        birthday = birthday,
        career = career,
        region = region,
        school = school,
        background = background
    )

    private fun UserAccount.toEntity(fallbackPassword: String = ""): UserEntity = UserEntity(
        phone = phone,
        avatar = avatar,
        nickname = nickname,
        password = password ?: fallbackPassword,
        introduction = introduction,
        sex = sex,
        birthday = birthday,
        career = career,
        region = region,
        school = school,
        background = background,
        authToken = null,
        refreshToken = null,
        last_Login = System.currentTimeMillis()
    )
}
