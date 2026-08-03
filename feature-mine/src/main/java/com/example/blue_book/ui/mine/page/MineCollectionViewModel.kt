package com.example.blue_book.ui.mine.page

import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MineCollectionViewModel @Inject constructor(
) : UdfViewModel<MineCollectionIntent, MineCollectionUiState, MineCollectionEffect>(MineCollectionUiState()) {

	override suspend fun handleIntent(intent: MineCollectionIntent) {
		when (intent) {
			MineCollectionIntent.Init -> initLoad()
			MineCollectionIntent.Refresh -> refresh()
			MineCollectionIntent.LoadMore -> { /* 暂无分页数据源 */ }
			is MineCollectionIntent.ToggleCollect -> { /* 暂无 API */ }
		}
	}

	private suspend fun initLoad() {
		setState { copy(isLoading = false, items = emptyList()) }
	}

	private suspend fun refresh() {
		setState { copy(isLoading = false, items = emptyList()) }
	}
}
