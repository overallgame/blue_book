package com.example.blue_book.data.remote

import com.example.blue_book.data.remote.dto.ScanResolveDto
import com.example.blue_book.network.ApiGateway
import javax.inject.Inject

/**
 * 扫码校验的远端入口。**只做"发请求 + 拆信封"**，不解释业务含义——
 * 失败是 HTTP 状态码还是业务码由 `apiResult` 统一处理，映射在
 * [com.example.blue_book.data.repository.ScanRepositoryImpl]。
 */
class ScanRemoteDataSource @Inject constructor(
	private val apiGateway: ApiGateway
) {
	private val api = apiGateway.createApi(ScanApi::class.java)

	suspend fun resolve(payload: String): Result<ScanResolveDto> =
		apiGateway.apiResult { api.resolve(payload) }
}
