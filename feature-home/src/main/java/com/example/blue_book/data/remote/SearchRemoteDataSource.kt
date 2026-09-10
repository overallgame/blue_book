package com.example.blue_book.data.remote

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.network.data.ApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

interface SearchApi {
	@GET("/api/v2/search/hot")
	suspend fun hotSearches(): Response<ApiResponse<List<String>>>

	@GET("/api/v2/search/suggest")
	suspend fun suggest(
		@Query("keyword") keyword: String? = null,
		@Query("limit") limit: Int? = null
	): Response<ApiResponse<List<String>>>
}

class SearchRemoteDataSource @Inject constructor(
	private val apiGateway: ApiGateway
) {
	private val api = apiGateway.createApi(SearchApi::class.java)

	/** 热搜词（无数据时返回空，端上回退静态推荐词） */
	suspend fun hotSearches(): Result<List<String>> =
		apiGateway.apiResult { api.hotSearches() }

	/** 猜你想搜：无 keyword 拉热门推荐，有 keyword 拉匹配联想 */
	suspend fun suggest(keyword: String?): Result<List<String>> =
		apiGateway.apiResult { api.suggest(keyword, null) }
}
