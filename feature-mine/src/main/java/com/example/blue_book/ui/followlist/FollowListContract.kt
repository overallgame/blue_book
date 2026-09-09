package com.example.blue_book.ui.followlist

import com.example.blue_book.data.UserAccount
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface FollowListIntent : UiIntent {
	data object Init : FollowListIntent
	data object Refresh : FollowListIntent
	data object LoadMore : FollowListIntent
	data class ToggleFollow(val user: UserAccount) : FollowListIntent
}

data class FollowListUiState(
	val items: List<UserAccount> = emptyList(),
	val isLoading: Boolean = false,
	val isLoadingMore: Boolean = false,
	val message: String? = null,
	val cursorId: Long? = null,
	val hasMore: Boolean = true,
	val pageSize: Int = 20
) : UiState

sealed interface FollowListEffect : UiEffect {
	data class ShowToast(val message: String) : FollowListEffect
}
