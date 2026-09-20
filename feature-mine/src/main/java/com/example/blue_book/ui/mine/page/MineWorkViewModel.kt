package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.VideoCardListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MineWorkViewModel @Inject constructor(
	private val currentUser: CurrentUser,
	private val videoProvider: IVideoProvider,
	interactionBus: VideoInteractionBus
) : VideoCardListViewModel<MineWorkIntent, MineWorkUiState, MineWorkEffect>(MineWorkUiState(), interactionBus) {

	private val togglingAids = mutableSetOf<Long>()

	override suspend fun handleIntent(intent: MineWorkIntent) {
		when (intent) {
			MineWorkIntent.Init -> initLoad()
			MineWorkIntent.Refresh -> refresh()
			MineWorkIntent.LoadMore -> loadMore()
			is MineWorkIntent.ToggleLike -> toggleLike(intent.item)
			is MineWorkIntent.DeleteItem -> deleteItem(intent.item)
		}
	}

	private val deletingAids = mutableSetOf<Long>()

	/** 删除自己的作品（软删 + 服务端清理点赞/收藏记录），成功后从列表移除 */
	private suspend fun deleteItem(item: VideoCardInfo) {
		if (item.aid in deletingAids) return
		deletingAids.add(item.aid)
		val result = videoProvider.deleteVideo(item.aid)
		result.onSuccess {
			setState { copy(items = items.filter { it.aid != item.aid }) }
			sendEffect(MineWorkEffect.ShowToast("已删除"))
		}
		result.onFailure { e ->
			sendEffect(MineWorkEffect.ShowToast(e.message ?: "删除失败"))
		}
		deletingAids.remove(item.aid)
	}

	private suspend fun initLoad() {
		val userId = currentUser.userId ?: 0L
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchUserVideos(userId, cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun refresh() {
		val userId = currentUser.userId ?: 0L
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchUserVideos(userId, cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		val userId = currentUser.userId ?: 0L
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchUserVideos(userId, cursorId = state.cursorId, size = state.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
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
		replaceCard(updated)
		sendEffect(MineWorkEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, newStatus)
		result.onFailure { e ->
			replaceCard(item)
			sendEffect(MineWorkEffect.UpdateItem(item))
			sendEffect(MineWorkEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	/** 广播变更已落到列表 state：发本页的 UpdateItem effect 局部刷新对应卡片 */
	override suspend fun onInteractionSynced(updated: VideoCardInfo) {
		sendEffect(MineWorkEffect.UpdateItem(updated))
	}
}
