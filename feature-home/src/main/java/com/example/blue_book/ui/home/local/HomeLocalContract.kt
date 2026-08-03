package com.example.blue_book.ui.home.local

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface HomeLocalIntent : UiIntent {
	data object Init : HomeLocalIntent
	data object Refresh : HomeLocalIntent
	data object LoadMore : HomeLocalIntent
	data class ToggleLike(val item: VideoCardInfo) : HomeLocalIntent
}

data class HomeLocalUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null
) : UiState

sealed interface HomeLocalEffect : UiEffect {
	data class ShowToast(val message: String) : HomeLocalEffect
	data class UpdateItem(val item: VideoCardInfo) : HomeLocalEffect
}
