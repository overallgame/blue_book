package com.example.bluebook.search.controller

import com.example.bluebook.common.ApiResponse
import com.example.bluebook.search.service.SearchService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2")
class SearchController(private val searchService: SearchService) {
    @GetMapping("/search/hot")
    fun hotSearches(): ApiResponse<List<String>> =
        ApiResponse.ok(searchService.getHotSearches())

    /** 猜你想搜：无 keyword 返回热门推荐，有 keyword 返回匹配联想 */
    @GetMapping("/search/suggest")
    fun suggest(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ApiResponse<List<String>> =
        ApiResponse.ok(searchService.suggest(keyword, limit))
}
