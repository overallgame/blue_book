package com.example.blue_book.ui.message

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface MessageIntent : UiIntent {
	data object Init : MessageIntent
	data object Refresh : MessageIntent
	data object LoadMore : MessageIntent
	data class MarkRead(val id: Long) : MessageIntent

	/** 删除单条通知 */
	data class Delete(val id: Long) : MessageIntent

	/** 清空全部通知 */
	data object ClearAll : MessageIntent
}

enum class MessageType { Follow, Like, Comment, Collect, System }

data class MessageItem(
	val id: Long,
	val type: MessageType,
	val senderId: Long = 0,
	val avatar: String = "",
	val nickname: String = "",
	val content: String = "",
	val time: Long = 0L,
	val isRead: Boolean = false,
	/** 关联视频（点赞/评论/收藏类通知可跳转播放） */
	val videoId: Long? = null
)

data class MessageUiState(
	val items: List<MessageItem> = emptyList(),
	val isLoading: Boolean = false,
	val isEmpty: Boolean = true,
	val unreadCount: Int = 0,
	val message: String? = null,
	val cursorId: Long? = null,
	val hasMore: Boolean = true,
	val pageSize: Int = 20
) : UiState

sealed interface MessageEffect : UiEffect {
	data class ShowToast(val message: String) : MessageEffect
}
