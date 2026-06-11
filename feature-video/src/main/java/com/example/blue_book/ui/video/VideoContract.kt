package com.example.blue_book.ui.video

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface VideoIntent : UiIntent {
	data object InitRandom : VideoIntent
	data class InitSearch(val keyword: String) : VideoIntent
	data object LoadMore : VideoIntent
    data class RequestPlayUrl(val aid: Long, val cid: Long) : VideoIntent
    data class ToggleLike(val video: VideoCardInfo) : VideoIntent
    data class ToggleCollect(val video: VideoCardInfo) : VideoIntent
}

data class VideoUiState(
    val items: List<VideoCardInfo> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val mode: Mode = Mode.Random,
    val keyword: String = "",
    val cursorId: Long? = null,
    val hasMore: Boolean = true
): UiState {
	enum class Mode { Random, Search }
}

sealed interface VideoUiEffect : UiEffect {
	data class ShowToast(val message: String) : VideoUiEffect
    data class UpdateItem(val item: VideoCardInfo) : VideoUiEffect
}


