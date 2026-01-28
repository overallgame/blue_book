package com.example.blue_book.core.network

import com.example.blue_book.data.local.preference.AuthPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
	private val preferences: AuthPreferences
): Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val token = preferences.getAuthToken()?.trim().orEmpty()
		val original = chain.request()
		val path = original.url.encodedPath
		if (path.startsWith("/api/v2/auth/refresh")) {
			return chain.proceed(original)
		}
		val newReq = if (token.isNotBlank()) {
			original.newBuilder()
				.header("Authorization", "Bearer $token")
				.build()
		} else original
		return chain.proceed(newReq)
	}
}