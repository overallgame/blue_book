package com.example.blue_book.presentation.search

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.usecase.FetchVideosByKeywordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchResultViewModel @Inject constructor(
	private val fetchByKeyword: FetchVideosByKeywordUseCase
) : UdfViewModel<SearchIntent, SearchUiState, SearchUiEffect>(SearchUiState()) {

	override suspend fun handleIntent(intent: SearchIntent) {
		when (intent) {
			is SearchIntent.Init -> init(intent.keyword)
			SearchIntent.LoadMore -> loadMore()
			is SearchIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	private suspend fun init(keyword: String) {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, keyword = keyword) } },
			call = { fetchByKeyword(keyword) },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "搜索失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || state.keyword.isBlank()) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { fetchByKeyword(state.keyword) },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		setState {
			copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) item else it })
		}
		sendEffect(SearchUiEffect.UpdateItem(item))
	}

	private fun toUi(v: Video): VideoCardInfo {
		return VideoCardInfo(
			aid = v.aid,
			cid = v.cid,
			like = v.like,
			image = v.image,
			avatar = v.avatar,
			collection = v.collection,
			nickname = v.nickname,
			description = v.description,
			playUrl = "",
			isLike = false,
			isCollect = false
		)
	}
}


