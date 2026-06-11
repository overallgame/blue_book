package com.example.blue_book.network

/**
 * 统一的响应状态常量，全项目引用，避免硬编码字符串。
 */
object ResponseState {
    /** ApiResponse 成功码 */
    const val API_SUCCESS = 0

    /** CommonResult 成功码 */
    const val COMMON_SUCCESS = 200

    /** Token 过期 */
    const val TOKEN_EXPIRED = 1024

    /** 无权限 */
    const val FORBIDDEN = 403
}
