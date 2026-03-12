package com.example.blue_book.presentation.video

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.usecase.CollectVideoUseCase
import com.example.blue_book.domain.usecase.FetchPlayUrlUseCase
import com.example.blue_book.domain.usecase.FetchRandomVideosUseCase
import com.example.blue_book.domain.usecase.FetchVideosByKeywordUseCase
import com.example.blue_book.domain.usecase.LikeVideoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val fetchRandomVideos: FetchRandomVideosUseCase,
    private val fetchByKeyword: FetchVideosByKeywordUseCase,
    private val fetchPlayUrl: FetchPlayUrlUseCase,
    private val likeVideoUseCase: LikeVideoUseCase,
    private val collectVideoUseCase: CollectVideoUseCase
) : UdfViewModel<VideoIntent, VideoUiState, VideoUiEffect>(VideoUiState()) {

	override suspend fun handleIntent(intent: VideoIntent) {
		when (intent) {
			VideoIntent.InitRandom -> initRandom()
			is VideoIntent.InitSearch -> initSearch(intent.keyword)
			VideoIntent.LoadMore -> loadMore()
            is VideoIntent.RequestPlayUrl -> requestPlayUrl(intent.aid, intent.cid)
            is VideoIntent.ToggleLike -> toggleLike(intent.video)
            is VideoIntent.ToggleCollect -> toggleCollect(intent.video)
		}
	}

	private suspend fun initRandom() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, mode = VideoUiState.Mode.Random, keyword = "") } },
			call = { fetchRandomVideos() },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun initSearch(keyword: String) {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, mode = VideoUiState.Mode.Search, keyword = keyword) } },
			call = { fetchByKeyword(keyword) },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "搜索失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = {
				when (state.mode) {
					VideoUiState.Mode.Random -> fetchRandomVideos()
					VideoUiState.Mode.Search -> fetchByKeyword(state.keyword)
				}
			},
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
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

    private suspend fun requestPlayUrl(aid: Long, cid: Long) {
        val current = uiState.value.items
        val target = current.firstOrNull { it.aid == aid && it.cid == cid } ?: return
        if (target.playUrl.isNotBlank()) return
		runResult(
			call = { withContext(Dispatchers.IO) { fetchPlayUrl(aid, cid) } },
			onSuccess = { url -> sendEffect(VideoUiEffect.UpdateItem(target.copy(playUrl = url))) },
			onFailure = { e -> sendEffect(VideoUiEffect.ShowToast(e.message ?: "获取播放地址失败")) }
		)
    }

    private suspend fun toggleLike(video: VideoCardInfo) {
        val newStatus = !video.isLike
        val newLikeNumber = video.like + if (newStatus) 1 else -1
        val updatedVideo = video.copy(isLike = newStatus, like = newLikeNumber)

        // 先更新本地UI
        updateItemInList(updatedVideo)

        // 发送网络请求
        runResult(
            call = { withContext(Dispatchers.IO) { likeVideoUseCase(video.aid, newStatus) } },
            onSuccess = { /* 已经更新了 */ },
            onFailure = { e ->
                // 失败回滚
                updateItemInList(video)
                sendEffect(VideoUiEffect.ShowToast(e.message ?: "操作失败"))
            }
        )
    }

    private suspend fun toggleCollect(video: VideoCardInfo) {
        val newStatus = !video.isCollect
        val newCollectionNumber = video.collection + if (newStatus) 1 else -1
        val updatedVideo = video.copy(isCollect = newStatus, collection = newCollectionNumber)

        // 先更新本地UI
        updateItemInList(updatedVideo)

        // 发送网络请求
        runResult(
            call = { withContext(Dispatchers.IO) { collectVideoUseCase(video.aid, newStatus) } },
            onSuccess = { /* 已经更新了 */ },
            onFailure = { e ->
                // 失败回滚
                updateItemInList(video)
                sendEffect(VideoUiEffect.ShowToast(e.message ?: "操作失败"))
            }
        )
    }

    private fun updateItemInList(updatedVideo: VideoCardInfo) {
        setState {
            val newItems = items.map { if (it.aid == updatedVideo.aid) updatedVideo else it }
            copy(items = newItems)
        }
    }
}


