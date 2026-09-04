package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.UdfViewModel
import com.therouter.TheRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MineCollectionViewModel @Inject constructor(
) : UdfViewModel<MineCollectionIntent, MineCollectionUiState, MineCollectionEffect>(MineCollectionUiState()) {

	private val videoProvider: IVideoProvider get() = TheRouter.get(IVideoProvider::class.java)!!
	private val togglingAids = mutableSetOf<Long>()

	override suspend fun handleIntent(intent: MineCollectionIntent) {
		when (intent) {
			MineCollectionIntent.Init -> initLoad()
			MineCollectionIntent.Refresh -> refresh()
			MineCollectionIntent.LoadMore -> loadMore()
			is MineCollectionIntent.ToggleCollect -> toggleCollect(intent.item)
		}
	}

	private suspend fun initLoad() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchCollectedVideos(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchCollectedVideos(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchCollectedVideos(cursorId = state.cursorId, size = state.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun toggleCollect(item: VideoCardInfo) {
		if (item.aid in togglingAids) return
		togglingAids.add(item.aid)
		val newStatus = !item.isCollect
		val updated = item.copy(
			isCollect = newStatus,
			collection = item.collection + if (newStatus) 1 else -1
		)
		updateItemInList(updated)
		sendEffect(MineCollectionEffect.UpdateItem(updated))
		val result = videoProvider.collectVideo(item.aid, newStatus)
		result.onFailure { e ->
			updateItemInList(item)
			sendEffect(MineCollectionEffect.UpdateItem(item))
			sendEffect(MineCollectionEffect.ShowToast(e.message ?: "收藏失败"))
		}
		togglingAids.remove(item.aid)
	}

	private fun updateItemInList(updated: VideoCardInfo) {
		setState {
			copy(items = items.map { if (it.aid == updated.aid && it.cid == updated.cid) updated else it })
		}
	}
}
