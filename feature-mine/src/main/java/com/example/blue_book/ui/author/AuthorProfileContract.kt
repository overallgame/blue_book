package com.example.blue_book.ui.author

import com.example.blue_book.data.UserAccount
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.VideoCardListState

sealed interface AuthorProfileIntent : UiIntent {
	data object Init : AuthorProfileIntent
	data object Refresh : AuthorProfileIntent
	data object LoadMore : AuthorProfileIntent
	data object ToggleFollow : AuthorProfileIntent
	data class ToggleLike(val item: VideoCardInfo) : AuthorProfileIntent
}

data class AuthorProfileUiState(
	/** 目标用户信息（isFollowed 透传关注态） */
	val profile: UserAccount? = null,
	override val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val isLoadingMore: Boolean = false,
	val message: String? = null,
	val cursorId: Long? = null,
	val hasMore: Boolean = true,
	val pageSize: Int = 10,
	/** 关注请求进行中，防连点 */
	val isTogglingFollow: Boolean = false
) : VideoCardListState<AuthorProfileUiState> {

	override fun withItems(items: List<VideoCardInfo>) = copy(items = items)
}

sealed interface AuthorProfileEffect : UiEffect {
	data class ShowToast(val message: String) : AuthorProfileEffect
	data class UpdateItem(val item: VideoCardInfo) : AuthorProfileEffect

	/** 未登录点关注：弹登录引导卡片 */
	data object ShowLoginGuide : AuthorProfileEffect
}
