package com.example.blue_book.ui.search

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.UdfViewModel
import com.therouter.TheRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchResultViewModel @Inject constructor(
) : UdfViewModel<SearchIntent, SearchUiState, SearchUiEffect>(SearchUiState()) {

    private val videoProvider: IVideoProvider get() = TheRouter.get(IVideoProvider::class.java)!!

    override suspend fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Init -> init(intent.keyword)
            SearchIntent.LoadMore -> loadMore()
            is SearchIntent.ToggleLike -> toggleLike(intent.item)
        }
    }

    private suspend fun init(keyword: String) {
        runResult(
            onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, keyword = keyword) } },
            call = { videoProvider.fetchVideosByKeyword(keyword) },
            onSuccess = { list -> setState { copy(items = items + list, isLoading = false) } },
            onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "搜索失败") } }
        )
    }

    private suspend fun loadMore() {
        val state = uiState.value
        if (state.isLoading || state.keyword.isBlank()) return
        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = { videoProvider.fetchVideosByKeyword(state.keyword) },
            onSuccess = { list -> setState { copy(items = items + list, isLoading = false) } },
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
        sendEffect(SearchUiEffect.UpdateItem(updated))
        val result = videoProvider.likeVideo(item.aid, targetLiked)
        result.onFailure { e ->
            setState {
                copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) item else it })
            }
            sendEffect(SearchUiEffect.UpdateItem(item))
            sendEffect(SearchUiEffect.ShowToast(e.message ?: "点赞失败"))
        }
    }
}
