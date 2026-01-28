package com.example.blue_book.presentation.video

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.usecase.FetchPlayUrlUseCase
import com.example.blue_book.domain.usecase.FetchRandomVideosUseCase
import com.example.blue_book.domain.usecase.FetchVideosByKeywordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val fetchRandomVideos: FetchRandomVideosUseCase,
    private val fetchByKeyword: FetchVideosByKeywordUseCase,
    private val fetchPlayUrl: FetchPlayUrlUseCase
) : UdfViewModel<VideoIntent, VideoUiState, VideoUiEffect>(VideoUiState()) {

	override suspend fun handleIntent(intent: VideoIntent) {
		when (intent) {
			VideoIntent.InitRandom -> initRandom()
			is VideoIntent.InitSearch -> initSearch(intent.keyword)
			VideoIntent.LoadMore -> loadMore()
            is VideoIntent.RequestPlayUrl -> requestPlayUrl(intent.aid, intent.cid)
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
}


