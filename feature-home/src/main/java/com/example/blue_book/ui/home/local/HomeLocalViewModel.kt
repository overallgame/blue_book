package com.example.blue_book.ui.home.local

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.VideoCardListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeLocalViewModel @Inject constructor(
	private val currentUser: CurrentUser,
	private val videoProvider: IVideoProvider,
	interactionBus: VideoInteractionBus
) : VideoCardListViewModel<HomeLocalIntent, HomeLocalUiState, HomeLocalEffect>(HomeLocalUiState(), interactionBus) {

	private val togglingAids = mutableSetOf<Long>()

	/** 定位城市（空 = 未授权/失败，走随机流兜底） */
	private var region: String = ""

	override suspend fun handleIntent(intent: HomeLocalIntent) {
		when (intent) {
			HomeLocalIntent.Init -> initLoad()
			is HomeLocalIntent.InitRegion -> {
				region = intent.region
				refresh()
			}

			HomeLocalIntent.Refresh -> refresh()
			HomeLocalIntent.LoadMore -> loadMore()
			is HomeLocalIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	/** 有城市走本地流，否则随机流兜底 */
	private suspend fun fetchPage(cursorId: Long?): Result<List<VideoCardInfo>> {
		val pageSize = uiState.value.pageSize
		return if (region.isNotBlank()) {
			videoProvider.fetchRegionFeed(region, cursorId, pageSize)
		} else {
			videoProvider.fetchRandomVideos(cursorId, pageSize)
		}
	}

	private suspend fun initLoad() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { fetchPage(null) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { fetchPage(null) },
			onSuccess = { list ->
				setState {
					copy(
						items = list,
						isLoading = false,
						cursorId = list.lastOrNull()?.aid,
						hasMore = list.size >= pageSize,
						// 本地流无内容时给出提示
						message = if (region.isNotBlank() && list.isEmpty()) "该地区暂无内容" else null
					)
				}
			},
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { fetchPage(state.cursorId) },
			onSuccess = { list -> setState { copy(items = items + list, isLoading = false, cursorId = list.lastOrNull()?.aid, hasMore = list.size >= pageSize) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		// 游客可浏览视频，点赞需登录（Fragment 层 isGuest 异步同步，这里兜底）
		if (currentUser.userId == null) {
			sendEffect(HomeLocalEffect.ShowLoginGuide)
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
		sendEffect(HomeLocalEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, newStatus)
		result.onFailure { e ->
			replaceCard(item)
			sendEffect(HomeLocalEffect.UpdateItem(item))
			sendEffect(HomeLocalEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	/** 广播变更已落到列表 state：发本页的 UpdateItem effect 局部刷新对应卡片 */
	override suspend fun onInteractionSynced(updated: VideoCardInfo) {
		sendEffect(HomeLocalEffect.UpdateItem(updated))
	}
}
