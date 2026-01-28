package com.example.blue_book.core.network

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.local.preference.AuthPreferences
import com.example.blue_book.data.remote.auth.dto2.AuthV2RefreshRequestDto
import com.example.blue_book.data.remote.auth.dto2.AuthV2TokenResponseDto
import com.example.blue_book.di.NetworkModule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
	private val preferences: AuthPreferences,
	private val gson: Gson,
	@Named("refresh") private val refreshClient: OkHttpClient
) : Authenticator {
	private val lock = Any()

	override fun authenticate(route: Route?, response: Response): Request? {
		val request = response.request
		val path = request.url.encodedPath
		if (path.startsWith("/api/v2/auth/refresh")) return null

		if (responseCount(response) >= 2) return null

		val refreshToken = preferences.getRefreshToken()?.trim().orEmpty()
		if (refreshToken.isBlank()) return null

		synchronized(lock) {
			val currentToken = preferences.getAuthToken()?.trim().orEmpty()
			val reqToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
			if (currentToken.isNotBlank() && currentToken != reqToken) {
				return request.newBuilder()
					.header("Authorization", "Bearer $currentToken")
					.build()
			}

			val refreshResult = refresh(refreshToken) ?: return null
			preferences.setAuthToken(refreshResult.token)
			preferences.setRefreshToken(refreshResult.refreshToken)

			return request.newBuilder()
				.header("Authorization", "Bearer ${refreshResult.token}")
				.build()
		}
	}

	private fun refresh(refreshToken: String): AuthV2TokenResponseDto? {
		return try {
			val base = NetworkModule.BASE_URL.trimEnd('/')
			val url = "$base/api/v2/auth/refresh"
			val json = gson.toJson(AuthV2RefreshRequestDto(refreshToken))
			val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
			val req = Request.Builder()
				.url(url)
				.post(body)
				.build()
			refreshClient.newCall(req).execute().use { resp ->
				if (!resp.isSuccessful) return null
				val str = resp.body?.string().orEmpty()
				if (str.isBlank()) return null
				val type = object : TypeToken<ApiResponse<AuthV2TokenResponseDto>>() {}.type
				val parsed: ApiResponse<AuthV2TokenResponseDto> = gson.fromJson(str, type)
				if (parsed.code != 0) return null
				parsed.data ?: return null
			}
		} catch (_: Throwable) {
			null
		}
	}

	private fun responseCount(response: Response): Int {
		var res: Response? = response
		var count = 1
		while (res?.priorResponse != null) {
			count++
			res = res.priorResponse
		}
		return count
	}
}
