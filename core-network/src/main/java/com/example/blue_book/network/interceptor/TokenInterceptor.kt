package com.example.blue_book.network.interceptor

import com.example.blue_book.network.TokenHolder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
		// 冷启动：内存里还没有 token 时先等持久化恢复完成再决定是否带鉴权头。
		// 否则首批请求会以游客身份发出：需要鉴权的接口（如 /api/v2/me/*）返回 401 →
		// 触发会话清理（此refreshToken 也还没恢复）→ 已登录用户被静默登出。
		// 仅在无 token 时等待，且设上限，避免阻塞请求线程。
		if (tokenHolder.authToken == null) {
			runBlocking {
				withTimeoutOrNull(RESTORE_WAIT_MS) { tokenHolder.awaitLoaded() }
			}
		}
		val token = tokenHolder.authToken?.trim().orEmpty()
		if (token.isBlank()) return chain.proceed(original)
		return chain.proceed(
			original.newBuilder()
				.header("Authorization", "Bearer $token")
				.build()
		)
	}

	private companion object {
		/** 冷启动等待 token 恢复的上限（毫秒） */
		const val RESTORE_WAIT_MS = 2000L
	}
}
