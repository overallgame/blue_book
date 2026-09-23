package com.example.blue_book.ui.publish

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface PublishIntent : UiIntent {
	/**
	 * 从系统选择器选中视频。
	 *
	 * 传 URI 的**字符串形式**而不是 `Uri`：状态与意图里没有平台类型，ViewModel 才能在纯 JVM 上
	 * 构造（`Uri.parse` 在单元测试里会抛 not mocked），而"取消上传/进度不倒退/publish 只调一次"
	 * 这些规则恰恰只有测得到才守得住。
	 */
	data class SelectMedia(val uri: String) : PublishIntent

	/** 发布：分块上传 → complete → publish */
	data class Submit(val title: String, val description: String) : PublishIntent

	/** 进页面时查一次"有没有上次没传完的"（本地账本） */
	data object Init : PublishIntent

	/** 把上次未完成的那条填回表单（**不是**直接续传，见 [PublishUiState.resumable] 的说明） */
	data object OnContinueResumable : PublishIntent

	/** 不再提示这条未完成的上传，并把它作废（本地 + 服务端一起清） */
	data object OnDismissResumable : PublishIntent
}

enum class PublishPhase { IDLE, UPLOADING, PUBLISHING }

/**
 * 上次未完成的上传（来自本地账本）。
 *
 * [progress] 由本地分片账本算出，**不依赖网络**——这正是"进页面立刻能看到进度"的意义：
 * 服务端也能回答传到哪了（`listParts`），但那要等一次往返，而用户此刻只想看到数字。
 */
data class ResumableUpload(
	/** 内容 URI 的字符串形式（本地账本也是按它索引的） */
	val uri: String,
	val fileName: String,
	val progress: Int
)

data class PublishUiState(
	/** 已选视频的 URI 字符串；null 表示还没选 */
	val mediaUri: String? = null,
	val title: String = "",
	val description: String = "",
	val phase: PublishPhase = PublishPhase.IDLE,
	/** 分块上传进度 0-100，仅 UPLOADING 阶段有意义 */
	val progress: Int = 0,
	/**
	 * 上次未完成的上传，null 表示没有。
	 *
	 * 它的动作是"**填回表单**"而不是"直接接着传"：上传与发布是一次动作
	 * （`publish` 需要标题/描述，而且上传完不发布只会在服务端留下一个没人认领的文件）。
	 * 所以"继续"的正确含义是把上次那个文件放回输入框，用户填好标题点发布时，
	 * 上传会从断点接着走（`init` 会返回已落盘的分片）。
	 */
	val resumable: ResumableUpload? = null
) : UiState {
	val isBusy: Boolean get() = phase != PublishPhase.IDLE
}

sealed interface PublishEffect : UiEffect {
	data class ShowToast(val message: String) : PublishEffect
	data object PublishSuccess : PublishEffect
}
