package com.example.blue_book.ui.home.find

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.UdfViewModel
import com.therouter.TheRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeFindViewModel @Inject constructor(
) : UdfViewModel<HomeFindIntent, HomeFindUiState, HomeFindEffect>(HomeFindUiState()) {

    private val videoProvider: IVideoProvider get() = TheRouter.get(IVideoProvider::class.java)!!

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
            call = { videoProvider.fetchRandomVideos() },
            onSuccess = { list -> setState { copy(items = items + list, isLoading = false) } },
            onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
        )
    }

    private suspend fun refresh() {
        runResult(
            onStart = { setState { copy(items = emptyList(), isLoading = true, message = null) } },
            call = { videoProvider.fetchRandomVideos() },
            onSuccess = { list -> setState { copy(items = list, isLoading = false) } },
            onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
        )
    }

    private suspend fun loadMore() {
        val state = uiState.value
        if (state.isLoading) return
        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = { videoProvider.fetchRandomVideos() },
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
        sendEffect(HomeFindEffect.UpdateItem(updated))
        val result = videoProvider.likeVideo(item.aid, targetLiked)
        result.onFailure { e ->
            setState {
                copy(items = items.map { if (it.aid == item.aid && it.cid == item.cid) item else it })
            }
            sendEffect(HomeFindEffect.UpdateItem(item))
            sendEffect(HomeFindEffect.ShowToast(e.message ?: "点赞失败"))
        }
    }
}
