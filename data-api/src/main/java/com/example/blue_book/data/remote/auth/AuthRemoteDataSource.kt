package com.example.blue_book.data.remote.auth

import com.example.blue_book.data.remote.apiCall
import com.example.blue_book.data.remote.apiUnitCall
import com.example.blue_book.data.remote.auth.dto2.AuthV2RefreshRequestDto
import com.example.blue_book.data.remote.auth.dto2.AuthV2TokenResponseDto
import javax.inject.Inject

class AuthRemoteDataSource @Inject constructor(
	private val api: AuthApi
) {

	suspend fun refresh(refreshToken: String): Result<AuthV2TokenResponseDto> {
		return apiCall { api.refresh(AuthV2RefreshRequestDto(refreshToken)) }
	}

	suspend fun logout(): Result<Unit> {
		return apiUnitCall { api.logout() }
	}
}
