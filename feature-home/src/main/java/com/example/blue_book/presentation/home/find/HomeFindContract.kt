package com.example.blue_book.presentation.home.find

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UiEffect
import com.example.blue_book.core.udf.UiIntent
import com.example.blue_book.core.udf.UiState

sealed interface HomeFindIntent : UiIntent {
	data object Init : HomeFindIntent
	data object Refresh : HomeFindIntent
	data object LoadMore : HomeFindIntent
	data class ToggleLike(val item: VideoCardInfo) : HomeFindIntent
}

data class HomeFindUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null
) : UiState

sealed interface HomeFindEffect : UiEffect {
	data class ShowToast(val message: String) : HomeFindEffect
	data class UpdateItem(val item: VideoCardInfo) : HomeFindEffect
}


