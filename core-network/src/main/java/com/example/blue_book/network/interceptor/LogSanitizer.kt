package com.example.blue_book.network.interceptor

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException

/**
 * 日志拦截器 — 输出请求/响应详情，敏感 header 脱敏，body 超长截断。
 */
class LogSanitizer : Interceptor {

    private val sensitiveHeaders = setOf("Authorization", "Cookie", "Set-Cookie")
    private val maxBodyLogSize = 4096L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 打印请求
        val reqBody = request.body
        val reqStr = buildString {
            append("--> ${request.method} ${request.url}")
            append("\n${headersToString(request.headers)}")
            if (reqBody != null && reqBody.contentType() != null && isPlaintext(reqBody.contentType()!!)) {
                append("\n${bodyToString(reqBody)}")
            }
        }
        println(reqStr)

        val startMs = System.currentTimeMillis()
        val response = chain.proceed(request)
        val duration = System.currentTimeMillis() - startMs

        // 打印响应
        val respBody = response.body
        val respStr = buildString {
            append("<-- ${response.code} ${response.message} (${duration}ms)")
            append("\n${headersToString(response.headers)}")
            if (respBody != null && respBody.contentType() != null && isPlaintext(respBody.contentType()!!)) {
                val source = respBody.source()
                source.request(java.lang.Long.MAX_VALUE)
                var buffer = source.buffer
                if ("gzip".equals(response.header("Content-Encoding"), ignoreCase = true)) {
                    GzipSource(buffer.clone()).use { gzip ->
                        buffer = Buffer().apply { writeAll(gzip) }
                    }
                }
                val charset = respBody.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                val bodyStr = buffer.clone().readString(charset)
                append("\n${bodyStr.take(maxBodyLogSize.toInt())}")
                if (bodyStr.length > maxBodyLogSize) append("...<truncated>")
            }
        }
        println(respStr)

        return response
    }

    private fun headersToString(headers: Headers): String = buildString {
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = if (name in sensitiveHeaders) maskToken(headers.value(i))
            else headers.value(i)
            append("$name: $value\n")
        }
    }

    private fun bodyToString(body: okhttp3.RequestBody): String {
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            val str = buffer.readString(charset)
            str.take(maxBodyLogSize.toInt()).let {
                if (str.length > maxBodyLogSize) "$it...<truncated>" else it
            }
        } catch (e: Exception) {
            "[binary body: ${body.contentLength()} bytes]"
        }
    }

    private fun isPlaintext(mediaType: okhttp3.MediaType): Boolean {
        return when {
            mediaType.type == "text" -> true
            mediaType.subtype == "json" || mediaType.subtype == "xml" -> true
            mediaType.subtype == "x-www-form-urlencoded" -> true
            else -> false
        }
    }

    companion object {
        fun maskToken(token: String): String {
            if (token.length <= 8) return "***"
            return token.take(4) + "***" + token.takeLast(4)
        }
    }
}
