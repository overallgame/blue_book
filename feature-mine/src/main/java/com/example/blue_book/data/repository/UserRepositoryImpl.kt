package com.example.blue_book.data.repository

import android.net.Uri
import com.example.blue_book.data.UserAccount
import com.example.blue_book.data.mapper.toDomain
import com.example.blue_book.data.remote.file.FileRemoteDataSource
import com.example.blue_book.data.remote.user.UserRemoteDataSource
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.network.ApiGateway
import com.example.blue_book.network.TokenHolder
import com.example.blue_book.provider.IUserStore
import com.example.blue_book.util.UriFileResolver
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
    private val uriFileResolver: UriFileResolver,
    private val tokenHolder: TokenHolder
) : UserRepository {

    private val userStore: IUserStore get() = TheRouter.get(IUserStore::class.java)!!

    override suspend fun getUserProfile(phone: String): Result<UserAccount> {
        val remote = userRemote.me()
        return remote.fold(
            onSuccess = { dto ->
                val domain = dto.toDomain()
                val fallbackPassword = userStore.getUserByPhone(domain.phone)?.password
                    ?: userStore.getUserByPhone(phone)?.password ?: ""
                val domainWithPwd = domain.copy(password = fallbackPassword)
                val existing = userStore.getUserByPhone(domain.phone)
                if (existing == null) userStore.saveUser(domainWithPwd)
                else userStore.updateUser(domainWithPwd)
                tokenHolder.savePhone(domain.phone)
                Result.success(domain)
            },
            onFailure = {
                try {
                    val local = userStore.getUserByPhone(phone)
                        ?: throw IllegalStateException("本地不存在该用户信息")
                    Result.success(local)
                } catch (t: Throwable) { Result.failure(t) }
            }
        )
    }

    override suspend fun updateUserProfile(account: UserAccount): Result<Unit> {
        fun isLocalUri(s: String?) = !s.isNullOrBlank() && (s.startsWith("content://") || s.startsWith("file://"))
        fun toRelative(url: String?): String? {
            val v = url?.trim().orEmpty()
            if (v.isBlank()) return null
            val base = ApiGateway.BASE_URL.trimEnd('/')
            return if (v.startsWith(base)) v.removePrefix(base) else v
        }
        suspend fun uploadPart(uriStr: String, name: String, prefix: String): Result<MultipartBody.Part> {
            val uri = Uri.parse(uriStr)
            val file = uriFileResolver.copyToCacheFile(uri, prefix) ?: return Result.failure(IllegalStateException("无法读取图片"))
            val mime = uriFileResolver.mimeTypeOf(uri) ?: uriFileResolver.guessMimeType(file)
            return Result.success(MultipartBody.Part.createFormData(name, file.name, file.asRequestBody(mime.toMediaTypeOrNull())))
        }
        var bg = account.background
        if (isLocalUri(account.avatar)) {
            uploadPart(account.avatar!!, "avatar", "avatar")
                .mapCatching { part -> userRemote.uploadAvatar(part).getOrThrow().avatarUrl }
                .getOrElse { return Result.failure(it) }
        }
        if (isLocalUri(account.background)) {
            uploadPart(account.background!!, "file", "bg")
                .mapCatching { part -> fileRemote.uploadImage(part).getOrThrow() }
                .onSuccess { bg = it }
                .getOrElse { return Result.failure(it) }
        }
        val body = UserV2UpdateRequestDto(
            nickname = account.nickname?.trim()?.ifBlank { null },
            bio = account.introduction?.trim()?.ifBlank { null },
            gender = account.sex?.trim()?.ifBlank { null },
            birthday = account.birthday?.trim()?.ifBlank { null },
            occupation = account.career?.trim()?.ifBlank { null },
            region = account.region?.trim()?.ifBlank { null },
            school = account.school?.trim()?.ifBlank { null },
            backgroundImage = toRelative(bg)
        )
        return userRemote.updateMe(body).fold(
            onSuccess = { dto ->
                val updated = dto.toDomain()
                val pwd = account.password
                    ?: userStore.getUserByPhone(updated.phone)?.password
                    ?: userStore.getUserByPhone(account.phone)?.password ?: ""
                val updatedWithPwd = updated.copy(password = pwd)
                if (userStore.getUserByPhone(updated.phone) == null) userStore.saveUser(updatedWithPwd)
                else userStore.updateUser(updatedWithPwd)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun currentUserPhone(): String? = tokenHolder.phone
}
