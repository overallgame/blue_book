package com.example.blue_book.network.data

/**
* 统一的响应状态常量，全项目引用，避免硬编码字符串/数字。
* ApiCall / ApiGateway 判断成功码时引用这里，改协议只需改一处。
*/
object ResponseState {
	/** ApiResponse 成功码（ApiResponse.ok 的默认 code） */
	const val API_SUCCESS = 0

	/** CommonResult 成功码 */
	const val COMMON_SUCCESS = 200

	/** 无权限（HTTP 状态码；后端业务码 14001 也映射到此状态） */
	const val FORBIDDEN = 403
}
