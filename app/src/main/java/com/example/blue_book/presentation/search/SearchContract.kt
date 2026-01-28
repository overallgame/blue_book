package com.example.blue_book.presentation.search

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UiEffect
import com.example.blue_book.core.udf.UiIntent
import com.example.blue_book.core.udf.UiState

sealed interface SearchIntent : UiIntent {
	data class Init(val keyword: String) : SearchIntent
	data object LoadMore : SearchIntent
	data class ToggleLike(val item: VideoCardInfo) : SearchIntent
}

data class SearchUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val keyword: String = ""
) : UiState

sealed interface SearchUiEffect : UiEffect {
	data class ShowToast(val message: String) : SearchUiEffect
	data class UpdateItem(val item: VideoCardInfo) : SearchUiEffect
}


