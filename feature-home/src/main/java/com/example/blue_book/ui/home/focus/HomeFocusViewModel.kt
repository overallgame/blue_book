package com.example.blue_book.ui.home.focus

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.UdfViewModel
import com.therouter.TheRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeFocusViewModel @Inject constructor(
) : UdfViewModel<HomeFocusIntent, HomeFocusUiState, HomeFocusEffect>(HomeFocusUiState()) {

	private val videoProvider: IVideoProvider get() = TheRouter.get(IVideoProvider::class.java)!!
	private val togglingAids = mutableSetOf<Long>()

	override suspend fun handleIntent(intent: HomeFocusIntent) {
		when (intent) {
			HomeFocusIntent.Init -> initLoad()
			HomeFocusIntent.Refresh -> refresh()
			HomeFocusIntent.LoadMore -> loadMore()
			is HomeFocusIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	private suspend fun initLoad() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchFollowingFeed(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "请先登录") } }
		)
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchFollowingFeed(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "请先登录") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchFollowingFeed(state.cursorId, state.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "请先登录") } }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		if (item.aid in togglingAids) return
		togglingAids.add(item.aid)
		val newStatus = !item.isLike
		val updated = item.copy(
			isLike = newStatus,
			like = item.like + if (newStatus) 1 else -1
		)
		updateItemInList(updated)
		sendEffect(HomeFocusEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, newStatus)
		result.onFailure { e ->
			updateItemInList(item)
			sendEffect(HomeFocusEffect.UpdateItem(item))
			sendEffect(HomeFocusEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	private fun updateItemInList(updated: VideoCardInfo) {
		setState {
			copy(items = items.map { if (it.aid == updated.aid && it.cid == updated.cid) updated else it })
		}
	}
}
