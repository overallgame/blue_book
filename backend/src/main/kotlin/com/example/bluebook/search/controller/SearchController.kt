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
}
