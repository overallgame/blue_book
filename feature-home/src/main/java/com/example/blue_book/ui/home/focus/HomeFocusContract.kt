package com.example.blue_book.ui.home.focus

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.VideoCardListState

sealed interface HomeFocusIntent : UiIntent {
	data object Init : HomeFocusIntent
	data object Refresh : HomeFocusIntent
	data object LoadMore : HomeFocusIntent
	data class ToggleLike(val item: VideoCardInfo) : HomeFocusIntent
}

data class HomeFocusUiState(
	override val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val cursorId: Long? = null,
	val hasMore: Boolean = true,
	val pageSize: Int = 10
) : VideoCardListState<HomeFocusUiState> {

	override fun withItems(items: List<VideoCardInfo>) = copy(items = items)
}

sealed interface HomeFocusEffect : UiEffect {
	data class ShowToast(val message: String) : HomeFocusEffect
	data class UpdateItem(val item: VideoCardInfo) : HomeFocusEffect

	/** 未登录触发点赞：弹登录引导卡片 */
	data object ShowLoginGuide : HomeFocusEffect
}
