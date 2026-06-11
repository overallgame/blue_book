package com.example.blue_book.data.remote.user

import com.example.blue_book.data.remote.user.dto2.UserV2AvatarUploadResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2FollowListResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

class UserRemoteDataSource @Inject constructor(
    private val apiGateway: ApiGateway
) {
    private val api = apiGateway.createApi(UserApi::class.java)

    suspend fun me(): Result<UserV2MeDto> = apiGateway.apiResult { api.me() }

    suspend fun updateMe(body: UserV2UpdateRequestDto): Result<UserV2MeDto> =
        apiGateway.apiResult { api.updateMe(body) }

    suspend fun uploadAvatar(part: okhttp3.MultipartBody.Part): Result<UserV2AvatarUploadResponseDto> =
        apiGateway.apiResult { api.uploadAvatar(part) }

    suspend fun profile(userId: Long): Result<UserV2ProfileDto> =
        apiGateway.apiResult { api.profile(userId) }

    suspend fun follow(userId: Long): Result<Unit> =
        apiGateway.apiUnitResult { api.follow(userId) }

    suspend fun unfollow(userId: Long): Result<Unit> =
        apiGateway.apiUnitResult { api.unfollow(userId) }

    suspend fun followers(userId: Long, cursorId: Long?, size: Int?): Result<UserV2FollowListResponseDto> =
        apiGateway.apiResult { api.followers(userId, cursorId, size) }

    suspend fun following(userId: Long, cursorId: Long?, size: Int?): Result<UserV2FollowListResponseDto> =
        apiGateway.apiResult { api.following(userId, cursorId, size) }
}
