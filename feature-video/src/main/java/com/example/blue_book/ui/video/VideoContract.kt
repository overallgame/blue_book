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

	/** 播放失败后查询转码状态，用于区分"转码中"与真实失败 */
	data class CheckTranscode(val aid: Long, val originMessage: String) : VideoIntent

	/**
	 * 评论弹层关闭后同步评论数增量：
	 * 更新 state 中条目（保持与 adapter 一致，防止后续 UpdateItem effect 用旧值回退）
	 */
	data class AdjustCommentCount(val aid: Long, val delta: Int) : VideoIntent
	data class ToggleLike(val video: VideoCardInfo) : VideoIntent
	data class ToggleCollect(val video: VideoCardInfo) : VideoIntent
	data class ToggleFollow(val video: VideoCardInfo) : VideoIntent

	/** 播放量上报：滑动到某视频开始播放时触发（每视频每会话仅上报一次） */
	data class ReportView(val aid: Long) : VideoIntent

	/**
	 * 从来源列表进入播放页：首屏为用户点击的那条视频，
	 * 后续 loadMore 按 [VideoUiState.Mode] 用来源游标续拉
	 */
	data class InitFromSource(
		val mode: VideoUiState.Mode,
		val firstVideo: VideoCardInfo,
		val keyword: String = "",
		val userId: Long = 0L
	) : VideoIntent
}

data class VideoUiState(
	val items: List<VideoCardInfo> = emptyList(),
	val isLoading: Boolean = false,
	val message: String? = null,
	val mode: Mode = Mode.Random,
	val keyword: String = "",
	val hasMore: Boolean = true
): UiState {
	enum class Mode { Random, Search, Liked, Collected, UserVideos }
}

sealed interface VideoUiEffect : UiEffect {
	data class ShowToast(val message: String) : VideoUiEffect
	data class UpdateItem(val item: VideoCardInfo) : VideoUiEffect
}


