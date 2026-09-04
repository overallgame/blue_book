package com.example.blue_book.domain.repository

import com.example.blue_book.data.UserAccount

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
}
