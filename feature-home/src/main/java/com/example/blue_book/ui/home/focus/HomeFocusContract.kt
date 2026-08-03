package com.example.blue_book.ui.home.focus

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface HomeFocusIntent : UiIntent {
	data object Init : HomeFocusIntent
	data object Refresh : HomeFocusIntent
	data object LoadMore : HomeFocusIntent
	data class ToggleLike(val item: VideoCardInfo) : HomeFocusIntent
}

data class HomeFocusUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null
) : UiState

sealed interface HomeFocusEffect : UiEffect {
	data class ShowToast(val message: String) : HomeFocusEffect
	data class UpdateItem(val item: VideoCardInfo) : HomeFocusEffect
}
