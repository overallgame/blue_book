package com.example.blue_book.network.interceptor

import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response

/**
* 公共参数拦截器：自动为每个请求附加设备信息、版本号等通用参数。
* 注意：不要附加时间戳这类每次都变化的参数，否则 OkHttp 磁盘缓存永远命不中。
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
			.apply {
				if (channel.isNotBlank()) {
					addQueryParameter("channel", channel)
				}
			}
			.build()

		return chain.proceed(original.newBuilder().url(newUrl).build())
	}
}
