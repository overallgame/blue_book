package com.example.blue_book.data.remote

import com.example.blue_book.common.bean.ApiResponse
import com.example.blue_book.data.remote.account.dto.CommonResultDto
import retrofit2.HttpException
import retrofit2.Response

suspend inline fun <T> apiCall(
	crossinline call: suspend () -> Response<ApiResponse<T>>
): Result<T> {
	return try {
		val response = call()
		if (!response.isSuccessful) return Result.failure(HttpException(response))
		val body = response.body() ?: return Result.failure(IllegalStateException("响应体为空"))
		if (body.code != 0) return Result.failure(IllegalStateException("code=${body.code}, msg=${body.message}"))
		val data = body.data ?: return Result.failure(IllegalStateException("响应体为空"))
		Result.success(data)
	} catch (t: Throwable) {
		Result.failure(t)
	}
}

suspend inline fun apiUnitCall(
	crossinline call: suspend () -> Response<ApiResponse<Any>>
): Result<Unit> {
	return try {
		val response = call()
		if (!response.isSuccessful) return Result.failure(HttpException(response))
		val body = response.body() ?: return Result.failure(IllegalStateException("响应体为空"))
		if (body.code != 0) return Result.failure(IllegalStateException("code=${body.code}, msg=${body.message}"))
		Result.success(Unit)
	} catch (t: Throwable) {
		Result.failure(t)
	}
}

suspend inline fun <T> commonCall(
	crossinline call: suspend () -> Response<CommonResultDto<T>>
): Result<T> {
	return try {
		val response = call()
		if (!response.isSuccessful) return Result.failure(HttpException(response))
		val body = response.body() ?: return Result.failure(IllegalStateException("响应体为空"))
		val code = body.code
		if (code != 200) {
			val message = body.msg ?: "业务失败"
			return Result.failure(IllegalStateException("code=${code ?: -1}, msg=$message"))
		}
		val data = body.data ?: return Result.failure(IllegalStateException("响应体为空"))
		Result.success(data)
	} catch (t: Throwable) {
		Result.failure(t)
	}
}
