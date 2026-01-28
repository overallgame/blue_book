package com.example.blue_book.data.remote.user

import com.example.blue_book.data.remote.apiCall
import com.example.blue_book.data.remote.apiUnitCall
import com.example.blue_book.data.remote.user.dto2.UserV2AvatarUploadResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2FollowListResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import javax.inject.Inject

class UserRemoteDataSource @Inject constructor(
    private val api: UserApi
) {

    suspend fun me(): Result<UserV2MeDto> {
        return apiCall { api.me() }
    }

    suspend fun updateMe(body: UserV2UpdateRequestDto): Result<UserV2MeDto> {
        return apiCall { api.updateMe(body) }
    }

    suspend fun uploadAvatar(part: okhttp3.MultipartBody.Part): Result<UserV2AvatarUploadResponseDto> {
        return apiCall { api.uploadAvatar(part) }
    }

    suspend fun profile(userId: Long): Result<UserV2ProfileDto> {
        return apiCall { api.profile(userId) }
    }

    suspend fun follow(userId: Long): Result<Unit> {
        return apiUnitCall { api.follow(userId) }
    }

    suspend fun unfollow(userId: Long): Result<Unit> {
        return apiUnitCall { api.unfollow(userId) }
    }

    suspend fun followers(userId: Long, cursorId: Long?, size: Int?): Result<UserV2FollowListResponseDto> {
        return apiCall { api.followers(userId, cursorId, size) }
    }

    suspend fun following(userId: Long, cursorId: Long?, size: Int?): Result<UserV2FollowListResponseDto> {
        return apiCall { api.following(userId, cursorId, size) }
    }
}
