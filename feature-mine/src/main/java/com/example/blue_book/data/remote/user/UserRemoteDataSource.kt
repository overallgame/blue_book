package com.example.blue_book.data.remote.user

import com.example.blue_book.data.remote.user.dto2.BioUpdateRequest
import com.example.blue_book.data.remote.user.dto2.BirthdayUpdateRequest
import com.example.blue_book.data.remote.user.dto2.GenderUpdateRequest
import com.example.blue_book.data.remote.user.dto2.NicknameUpdateRequest
import com.example.blue_book.data.remote.user.dto2.OccupationUpdateRequest
import com.example.blue_book.data.remote.user.dto2.RegionUpdateRequest
import com.example.blue_book.data.remote.user.dto2.SchoolUpdateRequest
import com.example.blue_book.data.remote.user.dto2.UserV2AvatarUploadResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2FollowListResponseDto
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto
import com.example.blue_book.data.remote.user.dto2.UserV2UpdateRequestDto
import com.example.blue_book.network.ApiGateway
import okhttp3.MultipartBody
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

	suspend fun updateNickname(body: NicknameUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateNickname(body) }

	suspend fun updateBio(body: BioUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateBio(body) }

	suspend fun updateGender(body: GenderUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateGender(body) }

	suspend fun updateBirthday(body: BirthdayUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateBirthday(body) }

	suspend fun updateOccupation(body: OccupationUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateOccupation(body) }

	suspend fun updateRegion(body: RegionUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateRegion(body) }

	suspend fun updateSchool(body: SchoolUpdateRequest): Result<UserV2MeDto> =
		apiGateway.apiResult { api.updateSchool(body) }

	suspend fun uploadBackground(part: MultipartBody.Part): Result<UserV2AvatarUploadResponseDto> =
		apiGateway.apiResult { api.uploadBackground(part) }
}
