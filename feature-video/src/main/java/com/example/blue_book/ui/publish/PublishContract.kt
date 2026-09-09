package com.example.blue_book.ui.publish

import android.net.Uri
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface PublishIntent : UiIntent {
	/** 从系统选择器选中视频 */
	data class SelectMedia(val uri: Uri) : PublishIntent

	/** 发布：分块上传 → complete → publish */
	data class Submit(val title: String, val description: String) : PublishIntent
}

enum class PublishPhase { IDLE, UPLOADING, PUBLISHING }

data class PublishUiState(
	val mediaUri: Uri? = null,
	val title: String = "",
	val description: String = "",
	val phase: PublishPhase = PublishPhase.IDLE,
	/** 分块上传进度 0-100，仅 UPLOADING 阶段有意义 */
	val progress: Int = 0
) : UiState {
	val isBusy: Boolean get() = phase != PublishPhase.IDLE
}

sealed interface PublishEffect : UiEffect {
	data class ShowToast(val message: String) : PublishEffect
	data object PublishSuccess : PublishEffect
}
