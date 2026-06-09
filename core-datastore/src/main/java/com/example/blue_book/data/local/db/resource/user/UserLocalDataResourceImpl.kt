package com.example.blue_book.data.local.db.resource.user

import com.example.blue_book.data.local.db.dao.UserDao
import com.example.blue_book.data.local.db.entity.UserEntity
import com.example.blue_book.data.local.preference.AuthPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLocalDataResourceImpl @Inject constructor(
    private val userDao: UserDao,
    private val preferences: AuthPreferences
) : UserLocalDataResource {

    override suspend fun saveUser(userEntity: UserEntity) {
        userDao.insert(userEntity)
        preferences.setPhone(userEntity.phone)
    }

    override suspend fun updateUser(userEntity: UserEntity) {
        userDao.update(userEntity)
    }

    override suspend fun getCurrentUser(phone: String): UserEntity? {
        return userDao.getCurrentUser(phone)
    }

    override suspend fun clearUser() {
        val phone = preferences.getPhone()
        if (!phone.isNullOrBlank()) {
            userDao.getCurrentUser(phone)?.let { userDao.delete(it) }
        }
        preferences.clear()
    }

    override fun getCurrentUserPhone(): String? {
        return preferences.getPhone()
    }
}