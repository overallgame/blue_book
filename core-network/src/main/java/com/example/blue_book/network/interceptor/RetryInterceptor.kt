package com.example.blue_book.network.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow

/**
* 请求重试拦截器 — 指数退避，适用于临时性网络故障。
* 仅重试幂等请求（GET/HEAD）和 IOException。
*
* 重试属于异常路径，日志用 Log.w 且不按变体关闭：正式包里出现重试是有价值的信息，
* 且内容只有重试次数与异常消息，不含请求体。
*/
class RetryInterceptor(
	private val maxRetries: Int = 3,
	private val initialBackoffMs: Long = 1000L
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		var lastException: IOException? = null

		for (attempt in 0..maxRetries) {
			try {
				val response = chain.proceed(chain.request())
				if (attempt == 0) return response
				// 重试成功
				Log.w(TAG, "第${attempt}次重试成功")
				return response
			} catch (e: IOException) {
				lastException = e
				if (!shouldRetry(chain.request().method) || attempt == maxRetries) throw e

				val delay = min(initialBackoffMs * 2.0.pow(attempt).toLong(), 30_000L)
				Log.w(TAG, "请求失败(第${attempt + 1}次)，${delay}ms 后重试: ${e.message}")
				Thread.sleep(delay)
			}
		}

		throw lastException!!
	}

	private fun shouldRetry(method: String): Boolean {
		return method == "GET" || method == "HEAD"
	}

	private companion object {
		const val TAG = "RetryInterceptor"
	}
}
