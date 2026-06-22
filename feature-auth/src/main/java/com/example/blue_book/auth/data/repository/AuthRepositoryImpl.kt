package com.example.blue_book.auth.data.repository

import com.example.blue_book.auth.data.mapper.toDomain
import com.example.blue_book.auth.data.remote.AuthRemoteDataSource
import com.example.blue_book.auth.data.remote.dto.LoginRequest
import com.example.blue_book.auth.domain.model.LoginCredentials
import com.example.blue_book.auth.domain.model.RegisterInfo
import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.data.UserAccount
import com.example.blue_book.network.TokenHolder
import com.example.blue_book.network.datasource.TokenRemoteDataSource
import com.example.blue_book.provider.IUserStore
import com.therouter.TheRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource,
    private val authRemote: TokenRemoteDataSource,
    private val tokenHolder: TokenHolder
) : AuthRepository {

    private val userStore: IUserStore get() = TheRouter.get(IUserStore::class.java)!!

    override suspend fun isLoggedIn(): Boolean {
        val phone = tokenHolder.phone
        val token = tokenHolder.authToken
        return phone != null && !token.isNullOrBlank()
    }

    override suspend fun login(credentials: LoginCredentials): Result<UserAccount> {
        val remoteResult = remoteDataSource.login(LoginRequest(credentials.phone, credentials.password))
        return remoteResult.fold(
            onSuccess = { dto ->
                dto.token?.let { tokenHolder.saveAuthToken(it) }
                dto.refreshToken?.let { tokenHolder.saveRefreshToken(it) }
                val account = dto.profile?.toDomain() ?: defaultAccount(credentials.phone)
                userStore.saveUser(account)
                tokenHolder.savePhone(account.phone)
                Result.success(account)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun logout(): Result<Unit> {
        try { authRemote.logout() } catch (_: Throwable) {}
        return try {
            val phone = tokenHolder.phone
            if (!phone.isNullOrBlank()) {
                userStore.deleteUserByPhone(phone)
            }
            tokenHolder.clear()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun register(info: RegisterInfo): Result<UserAccount> {
        val remoteResult = remoteDataSource.register(
            info.nickname, info.phone, info.password, info.verificationCode
        )
        return remoteResult.fold(
            onSuccess = { dto ->
                dto.token?.let { tokenHolder.saveAuthToken(it) }
                dto.refreshToken?.let { tokenHolder.saveRefreshToken(it) }
                val account = dto.profile?.toDomain() ?: defaultAccount(info.phone, info.nickname)
                userStore.saveUser(account)
                tokenHolder.savePhone(account.phone)
                Result.success(account)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun sendVerificationCode(phone: String, nickname: String): Result<String> {
        return remoteDataSource.sendVerificationCode(phone, nickname)
    }

    private fun defaultAccount(phone: String, nickname: String? = null): UserAccount = UserAccount(
        phone = phone, nickname = nickname, avatar = null, password = null,
        introduction = null, sex = null, birthday = null, career = null,
        region = null, school = null, background = null
    )

}
