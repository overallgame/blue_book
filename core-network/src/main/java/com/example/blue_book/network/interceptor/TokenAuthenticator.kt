package com.example.blue_book.network.interceptor

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.network.TokenHolder
import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.network.dto.RefreshTokenRequest
import com.example.blue_book.network.dto.TokenResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class TokenAuthenticator(
    private val tokenHolder: TokenHolder
) : Authenticator {
    private val lock = Any()
    private val gson = Gson()

    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.url.encodedPath.startsWith("/api/v2/auth/refresh")) return null
        if (responseCount(response) >= 2) { tokenHolder.clear(); return null }

        val refresh = tokenHolder.refreshToken?.trim().orEmpty()
        if (refresh.isBlank()) { tokenHolder.clear(); return null }

        synchronized(lock) {
            val current = tokenHolder.authToken?.trim().orEmpty()
            val req = request.header("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
            if (current.isNotBlank() && current != req) {
                return request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val result = executeRefresh(refresh)
            if (result == null) { tokenHolder.clear(); return null }
            tokenHolder.saveAuthToken(result.token)
            tokenHolder.saveRefreshToken(result.refreshToken)
            return request.newBuilder().header("Authorization", "Bearer ${result.token}").build()
        }
    }

    private fun executeRefresh(refreshToken: String): TokenResponse? {
        return try {
            val base = ApiGateway.BASE_URL.trimEnd('/')
            val url = "$base/api/v2/auth/refresh"
            val json = gson.toJson(RefreshTokenRequest(refreshToken))
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(url).post(body).build()
            refreshClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val str = resp.body?.string().orEmpty()
                if (str.isBlank()) return null
                val type = object : TypeToken<ApiResponse<TokenResponse>>() {}.type
                val parsed: ApiResponse<TokenResponse> = gson.fromJson(str, type)
                if (parsed.code != 0) return null
                parsed.data
            }
        } catch (_: Throwable) { null }
    }

    private fun responseCount(response: Response): Int {
        var res: Response? = response
        var count = 1
        while (res?.priorResponse != null) { count++; res = res.priorResponse }
        return count
    }
}
