package com.example.blue_book.ui.home.find

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.VideoCardListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeFindViewModel @Inject constructor(
	private val currentUser: CurrentUser,
	private val videoProvider: IVideoProvider,
	interactionBus: VideoInteractionBus
) : VideoCardListViewModel<HomeFindIntent, HomeFindUiState, HomeFindEffect>(HomeFindUiState(), interactionBus) {

	private val togglingAids = mutableSetOf<Long>()

	override suspend fun handleIntent(intent: HomeFindIntent) {
		when (intent) {
			HomeFindIntent.Init -> initLoad()
			HomeFindIntent.Refresh -> refresh()
			HomeFindIntent.LoadMore -> loadMore()
			is HomeFindIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	/**
	 * 失败处理：列表为空时由页面的错误浮层承接（带重试入口），列表非空时补一个提示——
	 * 否则下拉刷新/加载更多失败只会默默停掉转圈，用户无法判断是否刷新成功。
	 */
	private suspend fun notifyFailure(e: Throwable) {
		val msg = e.message ?: "加载失败"
		setState { copy(isLoading = false, message = msg) }
		if (uiState.value.items.isNotEmpty()) sendEffect(HomeFindEffect.ShowToast(msg))
	}

	private suspend fun initLoad() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchRandomVideos(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> notifyFailure(e) }
		)
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { videoProvider.fetchRandomVideos(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list -> setState { copy(items = list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> notifyFailure(e) }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchRandomVideos(cursorId = state.cursorId, size = state.pageSize) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> notifyFailure(e) }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		// 游客可浏览视频，点赞需登录（Fragment 层 isGuest 异步同步，这里兜底）
		if (currentUser.userId == null) {
			sendEffect(HomeFindEffect.ShowLoginGuide)
			return
		}
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
		sendEffect(HomeFindEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, targetLiked)
		result.onFailure { e ->
			setState {
				copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) item else it })
			}
			sendEffect(HomeFindEffect.UpdateItem(item))
			sendEffect(HomeFindEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	/** 广播变更已落到列表 state：发本页的 UpdateItem effect 局部刷新对应卡片 */
	override suspend fun onInteractionSynced(updated: VideoCardInfo) {
		sendEffect(HomeFindEffect.UpdateItem(updated))
	}
}
