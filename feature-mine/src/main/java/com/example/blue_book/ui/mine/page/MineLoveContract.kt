package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface MineLoveIntent : UiIntent {
	data object Init : MineLoveIntent
	data object Refresh : MineLoveIntent
	data object LoadMore : MineLoveIntent
	data class ToggleLike(val item: VideoCardInfo) : MineLoveIntent
}

data class MineLoveUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val cursorId: Long? = null,
	val hasMore: Boolean = true,
	val pageSize: Int = 20
) : UiState

sealed interface MineLoveEffect : UiEffect {
	data class ShowToast(val message: String) : MineLoveEffect
	data class UpdateItem(val item: VideoCardInfo) : MineLoveEffect
}
