package com.example.blue_book.data.repository

import android.net.Uri
import com.example.blue_book.data.local.db.resource.user.UserLocalDataResource
import com.example.blue_book.data.mapper.toDomain
import com.example.blue_book.data.mapper.toEntity
import com.example.blue_book.data.remote.account.AccountRemoteDataSource
import com.example.blue_book.data.remote.account.dto.LoginRequestDto
import com.example.blue_book.data.remote.auth.AuthRemoteDataSource
import com.example.blue_book.data.remote.file.FileRemoteDataSource
import com.example.blue_book.data.remote.user.UserRemoteDataSource
import com.example.blue_book.di.NetworkModule
import com.example.blue_book.data.local.preference.AuthPreferences
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import com.example.blue_book.common.util.UriFileResolver
import com.example.blue_book.domain.model.LoginCredentials
import com.example.blue_book.domain.model.RegisterInfo
import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val remoteDataSource: AccountRemoteDataSource,
    private val userRemote: UserRemoteDataSource,
    private val fileRemote: FileRemoteDataSource,
    private val authRemote: AuthRemoteDataSource,
    private val localDataResource: UserLocalDataResource,
    private val preferences: AuthPreferences,
    private val uriFileResolver: UriFileResolver
) : UserRepository {

    override suspend fun isLoggedIn(): Boolean {
        return localDataResource.getCurrentUserPhone() != null
    }

    override suspend fun login(credentials: LoginCredentials): Result<UserAccount> {
        val remoteResult = remoteDataSource.login(LoginRequestDto(credentials.phone, credentials.password))
        return remoteResult.fold(
            onSuccess = { dto ->
                // 保存 token
                preferences.setAuthToken(dto.token)
                preferences.setRefreshToken(dto.refreshToken)
                // 资料落库（若后端返回）
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
                localDataResource.saveUser(account.toEntity(""))
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
            localDataResource.clearUser()
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
                localDataResource.saveUser(account.toEntity(""))
                Result.success(account)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun sendVerificationCode(phone: String, nickname: String): Result<String> {
        return remoteDataSource.sendVerificationCode(phone, nickname)
    }

    override suspend fun getUserProfile(phone: String): Result<UserAccount> {
        val remote = userRemote.me()
        return remote.fold(
            onSuccess = { dto ->
                val domain = dto.toDomain()
                val fallbackPassword = localDataResource.getCurrentUser(domain.phone)?.password
                    ?: localDataResource.getCurrentUser(phone)?.password
                    ?: ""
                val existing = localDataResource.getCurrentUser(domain.phone)
                if (existing == null) {
                    localDataResource.saveUser(domain.toEntity(fallbackPassword))
                } else {
                    localDataResource.updateUser(domain.toEntity(fallbackPassword))
                }
                Result.success(domain)
            },
            onFailure = {
                try {
                    val local = localDataResource.getCurrentUser(phone)
                        ?: throw IllegalStateException("本地不存在该用户信息")
                    Result.success(local.toDomain())
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }
        )
    }

    override suspend fun updateUserProfile(account: UserAccount): Result<Unit> {
        fun isLocalUri(s: String?): Boolean {
            val v = s?.trim().orEmpty()
            if (v.isBlank()) return false
            return v.startsWith("content://") || v.startsWith("file://")
        }

        fun toRelativeIfBackendUrl(url: String?): String? {
            val v = url?.trim().orEmpty()
            if (v.isBlank()) return null
            val base = NetworkModule.BASE_URL.trimEnd('/')
            return if (v.startsWith(base)) v.removePrefix(base) else v
        }

        suspend fun uploadAsPart(uriStr: String, formName: String, filenamePrefix: String): Result<MultipartBody.Part> {
            val uri = Uri.parse(uriStr)
            val file = uriFileResolver.copyToCacheFile(uri, filenamePrefix)
                ?: return Result.failure(IllegalStateException("无法读取图片"))
            val mimeStr = uriFileResolver.mimeTypeOf(uri) ?: uriFileResolver.guessMimeType(file)
            val mime = mimeStr.toMediaTypeOrNull()
            val body = file.asRequestBody(mime)
            val part = MultipartBody.Part.createFormData(formName, file.name, body)
            return Result.success(part)
        }

        var backgroundToUpdate: String? = account.background

        if (isLocalUri(account.avatar)) {
            val partResult = uploadAsPart(account.avatar!!, formName = "avatar", filenamePrefix = "avatar")
            val upload = partResult.fold(
                onSuccess = { part -> userRemote.uploadAvatar(part) },
                onFailure = { Result.failure(it) }
            )
            upload.getOrElse { return Result.failure(it) }.avatarUrl
        }

        if (isLocalUri(account.background)) {
            val partResult = uploadAsPart(account.background!!, formName = "file", filenamePrefix = "bg")
            val upload = partResult.fold(
                onSuccess = { part -> fileRemote.uploadImage(part) },
                onFailure = { Result.failure(it) }
            )
            val uploaded = upload.getOrElse { return Result.failure(it) }
            backgroundToUpdate = uploaded
        }

        val body = UserV2UpdateRequestDto(
            nickname = account.nickname?.trim()?.ifBlank { null },
            bio = account.introduction?.trim()?.ifBlank { null },
            gender = account.sex?.trim()?.ifBlank { null },
            birthday = account.birthday?.trim()?.ifBlank { null },
            occupation = account.career?.trim()?.ifBlank { null },
            region = account.region?.trim()?.ifBlank { null },
            school = account.school?.trim()?.ifBlank { null },
            backgroundImage = toRelativeIfBackendUrl(backgroundToUpdate)
        )

        val remote = userRemote.updateMe(body)
        return remote.fold(
            onSuccess = { dto ->
                try {
                    val updated = dto.toDomain()
                    val fallbackPassword = account.password
                        ?: localDataResource.getCurrentUser(updated.phone)?.password
                        ?: localDataResource.getCurrentUser(account.phone)?.password
                        ?: ""
                    val existing = localDataResource.getCurrentUser(updated.phone)
                    if (existing == null) {
                        localDataResource.saveUser(updated.toEntity(fallbackPassword))
                    } else {
                        localDataResource.updateUser(updated.toEntity(fallbackPassword))
                    }
                    Result.success(Unit)
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun currentUserPhone(): String? {
        return localDataResource.getCurrentUserPhone()
    }
}

