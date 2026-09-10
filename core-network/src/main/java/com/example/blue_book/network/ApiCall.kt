package com.example.blue_book.network

import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.network.dto.CommonResult
import com.example.blue_book.network.exception.NetworkException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * 非 2xx 统一转成带中文提示的异常（各 ViewModel 直接把 message 展示给用户）。
 * inline 函数需通过 @PublishedApi 访问。
 */
@PublishedApi
internal fun httpFailure(response: Response<*>): NetworkException {
	val code = response.code()
	val message = serverMessage(response) ?: when {
		code == 401 || code == 403 -> "登录状态已失效，请重新登录"
		code == 404 -> "内容不存在或已删除"
		code in 500..599 -> "服务器繁忙，请稍后重试"
		else -> "请求失败($code)"
	}
	return NetworkException(code, message)
}

/**
 * 解析服务端错误体里的业务文案。
 * 后端对业务异常返回非 2xx（密码错误 400、用户不存在 404、未登录 401 等），
 * 具体原因（"手机号或密码错误""验证码错误或已过期""视频正在转码中"）只在响应体 message 里，
 * 不解析就只能给用户看"请求失败(400)"。
 */
@PublishedApi
internal fun serverMessage(response: Response<*>): String? = try {
	val raw = response.errorBody()?.string().orEmpty()
	if (raw.isBlank()) {
		null
	} else {
		val parsed = Gson().fromJson(raw, Map::class.java) as? Map<*, *>
		(parsed?.get("message") as? String)?.trim()?.takeIf { it.isNotEmpty() && it != "success" }
	}
} catch (_: Throwable) {
	null
}

suspend inline fun <T> apiCall(
	crossinline call: suspend () -> Response<ApiResponse<T>>
): Result<T> {
	return try {
		val response = call()
		if (!response.isSuccessful) return Result.failure(httpFailure(response))
		val body = response.body() ?: return Result.failure(IllegalStateException("响应体为空"))
		if (body.code != 0) return Result.failure(IllegalStateException("code=${body.code}, msg=${body.message}"))
		val data = body.data ?: return Result.failure(IllegalStateException("响应体为空"))
		Result.success(data)
	} catch (e: CancellationException) {
		throw e
	} catch (t: Throwable) {
		Result.failure(t)
	}
}

suspend inline fun apiUnitCall(
	crossinline call: suspend () -> Response<ApiResponse<Any>>
): Result<Unit> {
	return try {
		val response = call()
		if (!response.isSuccessful) return Result.failure(httpFailure(response))
		val body = response.body() ?: return Result.failure(IllegalStateException("响应体为空"))
		if (body.code != 0) return Result.failure(IllegalStateException("code=${body.code}, msg=${body.message}"))
		Result.success(Unit)
	} catch (e: CancellationException) {
		throw e
	} catch (t: Throwable) {
		Result.failure(t)
	}
}

suspend inline fun <T> commonCall(
	crossinline call: suspend () -> Response<CommonResult<T>>
): Result<T> {
	return try {
		val response = call()
		if (!response.isSuccessful) return Result.failure(httpFailure(response))
		val body = response.body() ?: return Result.failure(IllegalStateException("响应体为空"))
		val code = body.code
		if (code != 200) {
			val message = body.msg ?: "业务失败"
			return Result.failure(IllegalStateException("code=${code ?: -1}, msg=$message"))
		}
		val data = body.data ?: return Result.failure(IllegalStateException("响应体为空"))
		Result.success(data)
	} catch (e: CancellationException) {
		throw e
	} catch (t: Throwable) {
		Result.failure(t)
	}
}
