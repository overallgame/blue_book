package com.example.blue_book.ui.search

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.VideoCardListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchResultViewModel @Inject constructor(
	private val videoProvider: IVideoProvider,
	interactionBus: VideoInteractionBus
) : VideoCardListViewModel<SearchIntent, SearchUiState, SearchUiEffect>(SearchUiState(), interactionBus) {

	private val togglingAids = mutableSetOf<Long>()

	override suspend fun handleIntent(intent: SearchIntent) {
		when (intent) {
			is SearchIntent.Init -> init(intent.keyword)
			SearchIntent.LoadMore -> loadMore()
			is SearchIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	private suspend fun init(keyword: String) {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, keyword = keyword, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchVideosByKeyword(keyword) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.isNotEmpty()) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "搜索失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || state.keyword.isBlank() || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchVideosByKeyword(state.keyword, state.cursorId) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.isNotEmpty()) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		if (item.aid in togglingAids) return
		togglingAids.add(item.aid)
		val targetLiked = !item.isLike
		val updated = item.copy(
			isLike = targetLiked,
			like = item.like + if (targetLiked) 1 else -1
		)
		setState {
			copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) updated else it })
		}
		sendEffect(SearchUiEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, targetLiked)
		result.onFailure { e ->
			setState {
				copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) item else it })
			}
			sendEffect(SearchUiEffect.UpdateItem(item))
			sendEffect(SearchUiEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	/** 广播变更已落到列表 state：发本页的 UpdateItem effect 局部刷新对应卡片 */
	override suspend fun onInteractionSynced(updated: VideoCardInfo) {
		sendEffect(SearchUiEffect.UpdateItem(updated))
	}
}
