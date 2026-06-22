package com.example.blue_book.network.interceptor

import com.example.blue_book.network.TokenHolder
import okhttp3.Interceptor
import okhttp3.Response

class TokenInterceptor(
    private val tokenHolder: TokenHolder
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.encodedPath.startsWith("/api/v2/auth/refresh")) {
            return chain.proceed(original)
        }
        val token = tokenHolder.authToken?.trim().orEmpty()
        if (token.isBlank()) return chain.proceed(original)
        return chain.proceed(
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
