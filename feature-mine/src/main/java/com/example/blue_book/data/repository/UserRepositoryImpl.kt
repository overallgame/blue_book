package com.example.blue_book.data.repository

import android.net.Uri
import com.example.blue_book.data.mapper.toDomain
import com.example.blue_book.data.remote.file.FileRemoteDataSource
import com.example.blue_book.data.remote.user.UserRemoteDataSource
import com.example.blue_book.network.ApiGateway
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import com.example.blue_book.util.UriFileResolver
import com.example.blue_book.data.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.provider.IUserDataProvider
import com.therouter.TheRouter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userRemote: UserRemoteDataSource,
    private val fileRemote: FileRemoteDataSource,
    private val uriFileResolver: UriFileResolver
) : UserRepository {

    private val userData: IUserDataProvider get() = TheRouter.get(IUserDataProvider::class.java)!!

    override suspend fun getUserProfile(phone: String): Result<UserAccount> {
        val remote = userRemote.me()
        return remote.fold(
            onSuccess = { dto ->
                val domain = dto.toDomain()
                val fallbackPassword = userData.getUserByPhone(domain.phone)?.password
                    ?: userData.getUserByPhone(phone)?.password
                    ?: ""
                val domainWithPwd = domain.copy(password = fallbackPassword)
                val existing = userData.getUserByPhone(domain.phone)
                if (existing == null) {
                    userData.saveUser(domainWithPwd)
                } else {
                    userData.updateUser(domainWithPwd)
                }
                Result.success(domain)
            },
            onFailure = {
                try {
                    val local = userData.getUserByPhone(phone)
                        ?: throw IllegalStateException("本地不存在该用户信息")
                    Result.success(local)
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
            val base = ApiGateway.BASE_URL.trimEnd('/')
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
                        ?: userData.getUserByPhone(updated.phone)?.password
                        ?: userData.getUserByPhone(account.phone)?.password
                        ?: ""
                    val updatedWithPwd = updated.copy(password = fallbackPassword)
                    val existing = userData.getUserByPhone(updated.phone)
                    if (existing == null) {
                        userData.saveUser(updatedWithPwd)
                    } else {
                        userData.updateUser(updatedWithPwd)
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
        return userData.getCurrentUserPhone()
    }
}
