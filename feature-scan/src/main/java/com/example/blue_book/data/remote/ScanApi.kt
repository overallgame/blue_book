package com.example.blue_book.data.remote

import com.example.blue_book.data.remote.dto.ScanResolveDto
import com.example.blue_book.network.data.ApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ScanApi {

	/**
	 * 校验一段扫码内容。
	 *
	 * `payload` 用 `@Query`：Retrofit 会做 URL 编码，所以原始字符串里的
	 * `#`、`?`、`&`、空格、中文都不会破坏请求（手拼 URL 时这些正是最容易漏的）。
	 */
	@GET("/api/v2/scan/resolve")
	suspend fun resolve(
		@Query("payload") payload: String
	): Response<ApiResponse<ScanResolveDto>>
}
