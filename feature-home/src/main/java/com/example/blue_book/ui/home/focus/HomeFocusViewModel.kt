package com.example.blue_book.ui.home.focus

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.VideoCardListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeFocusViewModel @Inject constructor(
	private val currentUser: CurrentUser,
	private val videoProvider: IVideoProvider,
	interactionBus: VideoInteractionBus
) : VideoCardListViewModel<HomeFocusIntent, HomeFocusUiState, HomeFocusEffect>(HomeFocusUiState(), interactionBus) {

	private val togglingAids = mutableSetOf<Long>()

	override suspend fun handleIntent(intent: HomeFocusIntent) {
		when (intent) {
			HomeFocusIntent.Init -> initLoad()
			HomeFocusIntent.Refresh -> refresh()
			HomeFocusIntent.LoadMore -> loadMore()
			is HomeFocusIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	/**
	 * 失败处理：列表为空时由页面的错误浮层承接（带重试入口），列表非空时补一个提示——
	 * 否则下拉刷新/加载更多失败只会默默停掉转圈，用户无法判断是否刷新成功。
	 */
	private suspend fun notifyFailure(e: Throwable) {
		val msg = e.message ?: "请先登录"
		setState { copy(isLoading = false, message = msg) }
		if (uiState.value.items.isNotEmpty()) sendEffect(HomeFocusEffect.ShowToast(msg))
	}

	private suspend fun initLoad() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchFollowingFeed(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> notifyFailure(e) }
		)
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchFollowingFeed(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> notifyFailure(e) }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchFollowingFeed(state.cursorId, state.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> notifyFailure(e) }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		// 游客可浏览视频，点赞需登录（Fragment 层 isGuest 异步同步，这里兜底）
		if (currentUser.userId == null) {
			sendEffect(HomeFocusEffect.ShowLoginGuide)
			return
		}
		if (item.aid in togglingAids) return
		togglingAids.add(item.aid)
		val newStatus = !item.isLike
		val updated = item.copy(
			isLike = newStatus,
			like = item.like + if (newStatus) 1 else -1
		)
		replaceCard(updated)
		sendEffect(HomeFocusEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, newStatus)
		result.onFailure { e ->
			replaceCard(item)
			sendEffect(HomeFocusEffect.UpdateItem(item))
			sendEffect(HomeFocusEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	/** 广播变更已落到列表 state：发本页的 UpdateItem effect 局部刷新对应卡片 */
	override suspend fun onInteractionSynced(updated: VideoCardInfo) {
		sendEffect(HomeFocusEffect.UpdateItem(updated))
	}
}
