package com.example.blue_book.network

import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.network.dto.CommonResult
import com.example.blue_book.network.exception.NetworkException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * 非 2xx 统一转成带中文提示的异常（各 ViewModel 直接把 message 展示给用户）。
 * inline 函数需通过 @PublishedApi 访问。
 */
@PublishedApi
internal fun httpFailure(response: Response<*>): NetworkException {
	val code = response.code()
	val message = when {
		code == 401 || code == 403 -> "登录状态已失效，请重新登录"
		code == 404 -> "内容不存在或已删除"
		code in 500..599 -> "服务器繁忙，请稍后重试"
		else -> "请求失败($code)"
	}
	return NetworkException(code, message)
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
