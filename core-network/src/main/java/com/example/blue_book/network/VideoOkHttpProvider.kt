package com.example.blue_book.network

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 视频播放专用 OkHttpClient 工厂（由 core-network 统一管理）。
 *
 * 与 ApiGateway.OkHttp 的差异：
 * - 不含 TokenInterceptor / TokenAuthenticator（视频 URL 无需鉴权）
 * - 不含 CommonParamsInterceptor（视频 CDN 不需要）
 * - 不含 RetryInterceptor（断流续传由 Media3 DefaultLoadErrorHandlingPolicy 按 byte-range 处理）
 * - 不含 LogSanitizer（视频流有大量 byte-range 请求，全量日志会淹没 logcat）
 * - HTTP 缓存 10MB（manifest/playlist 等小文件受益）
 * - HTTP/1.1 only（部分 CDN 对 HTTP/2 支持不佳）
 * - 连接池 6 空闲 + 2min keep-alive（预加载 + 多实例并发）
 * - 更长 read 超时 30s（视频流是长连接）
 */
object VideoOkHttpProvider {

    private const val CACHE_SIZE = 10L * 1024 * 1024
    private const val CACHE_DIR = "video_http_cache"
    private const val CONNECT_TIMEOUT_SEC = 10L
    private const val READ_TIMEOUT_SEC = 60L

    fun create(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(6, 2, TimeUnit.MINUTES))
            .cache(Cache(cacheDir, CACHE_SIZE))
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    @Volatile
    private var instance: OkHttpClient? = null

    fun getInstance(context: Context): OkHttpClient = instance ?: synchronized(this) {
        instance ?: create(context).also { instance = it }
    }
}
