package com.example.blue_book.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow

/**
 * 请求重试拦截器 — 指数退避，适用于临时性网络故障。
 * 仅重试幂等请求（GET/HEAD）和 IOException。
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
                println("[RetryInterceptor] 第${attempt}次重试成功")
                return response
            } catch (e: IOException) {
                lastException = e
                if (!shouldRetry(chain.request().method) || attempt == maxRetries) throw e

                val delay = min(initialBackoffMs * 2.0.pow(attempt).toLong(), 30_000L)
                println("[RetryInterceptor] 请求失败(第${attempt + 1}次)，${delay}ms 后重试: ${e.message}")
                Thread.sleep(delay)
            }
        }

        throw lastException!!
    }

    private fun shouldRetry(method: String): Boolean {
        return method == "GET" || method == "HEAD"
    }
}
