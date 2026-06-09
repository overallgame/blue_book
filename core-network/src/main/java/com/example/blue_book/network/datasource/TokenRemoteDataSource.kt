package com.example.blue_book.network.datasource

import com.example.blue_book.network.apiCall
import com.example.blue_book.network.apiUnitCall
import com.example.blue_book.network.api.TokenApi
import com.example.blue_book.network.dto.RefreshTokenRequest
import com.example.blue_book.network.dto.TokenResponse
import javax.inject.Inject

class TokenRemoteDataSource @Inject constructor(
    private val api: TokenApi
) {

    suspend fun refresh(refreshToken: String): Result<TokenResponse> {
        return apiCall { api.refresh(RefreshTokenRequest(refreshToken)) }
    }

    suspend fun logout(): Result<Unit> {
        return apiUnitCall { api.logout() }
    }
}
