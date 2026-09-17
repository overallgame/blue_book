package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MineWorkViewModel @Inject constructor(
	private val currentUser: CurrentUser,
	private val videoProvider: IVideoProvider
) : UdfViewModel<MineWorkIntent, MineWorkUiState, MineWorkEffect>(MineWorkUiState()) {

	private val togglingAids = mutableSetOf<Long>()

	/** 跨页互动同步：播放页内的点赞/收藏/评论数变化落到本列表 */
	init {
		viewModelScope.launch {
			VideoInteractionBus.patches.collect { patch -> applyInteraction(patch) }
		}
	}

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
		updateItemInList(updated)
		sendEffect(MineWorkEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, newStatus)
		result.onFailure { e ->
			updateItemInList(item)
			sendEffect(MineWorkEffect.UpdateItem(item))
			sendEffect(MineWorkEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	private fun updateItemInList(updated: VideoCardInfo) {
		setState {
			copy(items = items.map { if (it.aid == updated.aid && it.cid == updated.cid) updated else it })
		}
	}

	/** 播放页互动结果同步：更新 state 与对应卡片 */
	private suspend fun applyInteraction(patch: VideoInteractionBus.Patch) {
		val target = uiState.value.items.firstOrNull { it.aid == patch.aid } ?: return
		val updated = VideoInteractionBus.apply(target, patch) ?: return
		updateItemInList(updated)
		sendEffect(MineWorkEffect.UpdateItem(updated))
	}
}
