package com.example.blue_book.network.interceptor

import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 公共参数拦截器：自动为每个请求附加设备信息、版本号、时间戳等通用参数。
 */
class CommonParamsInterceptor(
    private val appVersion: String,
    private val channel: String = ""
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        val newUrl = url.newBuilder()
            .addQueryParameter("platform", "android")
            .addQueryParameter("appVersion", appVersion)
            .addQueryParameter("brand", Build.BRAND)
            .addQueryParameter("model", Build.MODEL)
            .addQueryParameter("osVersion", Build.VERSION.RELEASE)
            .addQueryParameter("timestamp", System.currentTimeMillis().toString())
            .apply {
                if (channel.isNotBlank()) {
                    addQueryParameter("channel", channel)
                }
            }
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
