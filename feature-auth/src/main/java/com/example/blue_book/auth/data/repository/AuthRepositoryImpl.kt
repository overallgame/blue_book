package com.example.blue_book.auth.data.repository

import com.example.blue_book.auth.data.mapper.toDomain
import com.example.blue_book.auth.data.remote.AuthRemoteDataSource
import com.example.blue_book.auth.data.remote.dto.LoginRequest
import com.example.blue_book.preference.AuthPreferences
import com.example.blue_book.auth.domain.model.LoginCredentials
import com.example.blue_book.auth.domain.model.RegisterInfo
import com.example.blue_book.data.UserAccount
import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.network.datasource.TokenRemoteDataSource
import com.example.blue_book.provider.IUserDataProvider
import com.therouter.TheRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource,
    private val authRemote: TokenRemoteDataSource,
    private val preferences: AuthPreferences
) : AuthRepository {

    private val userData: IUserDataProvider get() = TheRouter.get(IUserDataProvider::class.java)!!

    override suspend fun isLoggedIn(): Boolean {
        val phone = userData.getCurrentUserPhone()
        val token = preferences.getAuthToken()
        return phone != null && !token.isNullOrBlank()
    }

    override suspend fun login(credentials: LoginCredentials): Result<UserAccount> {
        val remoteResult = remoteDataSource.login(LoginRequest(credentials.phone, credentials.password))
        return remoteResult.fold(
            onSuccess = { dto ->
                preferences.setAuthToken(dto.token)
                preferences.setRefreshToken(dto.refreshToken)
                val account = dto.profile?.toDomain() ?: UserAccount(
                    phone = credentials.phone,
                    avatar = null,
                    nickname = null,
                    password = null,
                    introduction = null,
                    sex = null,
                    birthday = null,
                    career = null,
                    region = null,
                    school = null,
                    background = null
                )
                userData.saveUser(account)
                Result.success(account)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun logout(): Result<Unit> {
        try {
            authRemote.logout()
        } catch (_: Throwable) {
        }
        return try {
            userData.clearUser()
            Result.success(Unit)
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    override suspend fun register(info: RegisterInfo): Result<UserAccount> {
        val remoteResult = remoteDataSource.register(
            info.nickname,
            info.phone,
            info.password,
            info.verificationCode
        )
        return remoteResult.fold(
            onSuccess = { dto ->
                preferences.setAuthToken(dto.token)
                preferences.setRefreshToken(dto.refreshToken)
                val account = dto.profile?.toDomain() ?: UserAccount(
                    phone = info.phone,
                    avatar = null,
                    nickname = info.nickname,
                    password = null,
                    introduction = null,
                    sex = null,
                    birthday = null,
                    career = null,
                    region = null,
                    school = null,
                    background = null
                )
                userData.saveUser(account)
                Result.success(account)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun sendVerificationCode(phone: String, nickname: String): Result<String> {
        return remoteDataSource.sendVerificationCode(phone, nickname)
    }
}
