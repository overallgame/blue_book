package com.example.blue_book.ui.search

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.VideoCardListState

sealed interface SearchIntent : UiIntent {
	data class Init(val keyword: String) : SearchIntent
	data object LoadMore : SearchIntent
	data class ToggleLike(val item: VideoCardInfo) : SearchIntent
}

data class SearchUiState(
	override val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val keyword: String = "",
	val cursorId: Long? = null,
	val hasMore: Boolean = true
) : VideoCardListState<SearchUiState> {

	override fun withItems(items: List<VideoCardInfo>) = copy(items = items)
}

sealed interface SearchUiEffect : UiEffect {
	data class ShowToast(val message: String) : SearchUiEffect
	data class UpdateItem(val item: VideoCardInfo) : SearchUiEffect
}


