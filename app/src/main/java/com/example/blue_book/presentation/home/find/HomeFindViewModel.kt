package com.example.blue_book.presentation.home.find

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.usecase.FetchRandomVideosUseCase
import com.example.blue_book.domain.usecase.LikeVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeFindViewModel @Inject constructor(
	private val fetchRandomVideos: FetchRandomVideosUseCase,
	private val likeVideoUseCase: LikeVideoUseCase
) : UdfViewModel<HomeFindIntent, HomeFindUiState, HomeFindEffect>(HomeFindUiState()) {

	override suspend fun handleIntent(intent: HomeFindIntent) {
		when (intent) {
			HomeFindIntent.Init -> initLoad()
			HomeFindIntent.Refresh -> refresh()
			HomeFindIntent.LoadMore -> loadMore()
			is HomeFindIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	private suspend fun initLoad() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null) } },
			call = { fetchRandomVideos() },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null) } },
			call = { fetchRandomVideos() },
			onSuccess = { list -> setState { copy(items = list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { fetchRandomVideos() },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun toggleLike(item: VideoCardInfo) {
		val targetLiked = !item.isLike
		val updated = item.copy(
			isLike = targetLiked,
			like = item.like + if (targetLiked) 1 else -1
		)
		setState {
			copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) updated else it })
		}
		sendEffect(HomeFindEffect.UpdateItem(updated))
		val result = likeVideoUseCase(item.aid, item.cid, targetLiked)
		result.onFailure { e ->
			val rollback = item
			setState {
				copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) rollback else it })
			}
			sendEffect(HomeFindEffect.UpdateItem(rollback))
			sendEffect(HomeFindEffect.ShowToast(e.message ?: "点赞失败"))
		}
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


