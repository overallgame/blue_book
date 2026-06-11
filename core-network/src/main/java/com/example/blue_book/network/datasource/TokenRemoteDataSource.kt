package com.example.blue_book.network.datasource

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.network.api.TokenApi
import com.example.blue_book.network.dto.RefreshTokenRequest
import com.example.blue_book.network.dto.TokenResponse
import javax.inject.Inject

class TokenRemoteDataSource @Inject constructor(
    private val apiGateway: ApiGateway
) {
    private val api = apiGateway.createApi(TokenApi::class.java)

    suspend fun refresh(refreshToken: String): Result<TokenResponse> =
        apiGateway.apiResult { api.refresh(RefreshTokenRequest(refreshToken)) }

    suspend fun logout(): Result<Unit> =
        apiGateway.apiUnitResult { api.logout() }
}
