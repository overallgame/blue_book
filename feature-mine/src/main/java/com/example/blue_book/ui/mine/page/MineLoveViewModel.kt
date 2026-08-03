package com.example.blue_book.ui.mine.page

import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MineLoveViewModel @Inject constructor(
) : UdfViewModel<MineLoveIntent, MineLoveUiState, MineLoveEffect>(MineLoveUiState()) {

	override suspend fun handleIntent(intent: MineLoveIntent) {
		when (intent) {
			MineLoveIntent.Init -> initLoad()
			MineLoveIntent.Refresh -> refresh()
			MineLoveIntent.LoadMore -> { /* 暂无分页数据源 */ }
			is MineLoveIntent.ToggleLike -> { /* 暂无 API */ }
		}
	}

	private suspend fun initLoad() {
		setState { copy(isLoading = false, items = emptyList()) }
	}

	private suspend fun refresh() {
		setState { copy(isLoading = false, items = emptyList()) }
	}
}
