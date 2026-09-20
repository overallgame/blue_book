package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.VideoCardListState

sealed interface MineWorkIntent : UiIntent {
	data object Init : MineWorkIntent
	data object Refresh : MineWorkIntent
	data object LoadMore : MineWorkIntent
	data class ToggleLike(val item: VideoCardInfo) : MineWorkIntent
	data class DeleteItem(val item: VideoCardInfo) : MineWorkIntent
}

data class MineWorkUiState(
	override val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val cursorId: Long? = null,
	val hasMore: Boolean = true,
	val pageSize: Int = 10
) : VideoCardListState<MineWorkUiState> {

	override fun withItems(items: List<VideoCardInfo>) = copy(items = items)
}

sealed interface MineWorkEffect : UiEffect {
	data class ShowToast(val message: String) : MineWorkEffect
	data class UpdateItem(val item: VideoCardInfo) : MineWorkEffect
}
