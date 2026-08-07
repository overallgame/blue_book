package com.example.bluebook.common

data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "success",
    val ttl: Int = 0,
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T?): ApiResponse<T> = ApiResponse(data = data)
        fun ok(): ApiResponse<Any> = ApiResponse(data = null)
        fun fail(code: Int, message: String): ApiResponse<Any> =
            ApiResponse(code = code, message = message, data = null)
    }
}
