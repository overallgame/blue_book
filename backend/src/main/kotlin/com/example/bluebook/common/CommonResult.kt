package com.example.bluebook.common

data class CommonResult<T>(
    val msg: String?,
    val code: Int?,
    val data: T?
) {
    companion object {
        fun <T> ok(data: T?): CommonResult<T> = CommonResult(msg = "success", code = 200, data = data)
    }
}
