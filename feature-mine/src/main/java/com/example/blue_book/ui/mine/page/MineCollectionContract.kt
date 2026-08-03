package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface MineCollectionIntent : UiIntent {
	data object Init : MineCollectionIntent
	data object Refresh : MineCollectionIntent
	data object LoadMore : MineCollectionIntent
	data class ToggleCollect(val item: VideoCardInfo) : MineCollectionIntent
}

data class MineCollectionUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null
) : UiState

sealed interface MineCollectionEffect : UiEffect {
	data class ShowToast(val message: String) : MineCollectionEffect
	data class UpdateItem(val item: VideoCardInfo) : MineCollectionEffect
}
