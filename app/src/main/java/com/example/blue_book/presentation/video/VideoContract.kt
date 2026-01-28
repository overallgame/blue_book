package com.example.blue_book.presentation.video

import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.core.udf.UiEffect
import com.example.blue_book.core.udf.UiIntent
import com.example.blue_book.core.udf.UiState

sealed interface VideoIntent : UiIntent {
	data object InitRandom : VideoIntent
	data class InitSearch(val keyword: String) : VideoIntent
	data object LoadMore : VideoIntent
    data class RequestPlayUrl(val aid: Long, val cid: Long) : VideoIntent
}

data class VideoUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val mode: Mode = Mode.Random,
	val keyword: String = ""
): UiState {
	enum class Mode { Random, Search }
}

sealed interface VideoUiEffect : UiEffect {
	data class ShowToast(val message: String) : VideoUiEffect
    data class UpdateItem(val item: VideoCardInfo) : VideoUiEffect
}


