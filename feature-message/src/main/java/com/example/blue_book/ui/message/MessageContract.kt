package com.example.blue_book.ui.message

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface MessageIntent : UiIntent {
	data object Init : MessageIntent
	data object Refresh : MessageIntent
	data object LoadMore : MessageIntent
	data class MarkRead(val id: Long) : MessageIntent
}

enum class MessageType { Follow, Like, Comment, System }

data class MessageItem(
	val id: Long,
	val type: MessageType,
	val avatar: String = "",
	val nickname: String = "",
	val content: String = "",
	val time: Long = 0L,
	val isRead: Boolean = false,
	val thumbUrl: String = ""
)

data class MessageUiState(
	val items: List<MessageItem> = emptyList(),
	val isLoading: Boolean = false,
	val isEmpty: Boolean = true,
	val unreadCount: Int = 0,
	val message: String? = null
) : UiState

sealed interface MessageEffect : UiEffect {
	data class ShowToast(val message: String) : MessageEffect
	data class NavigateToVideo(val aid: Long) : MessageEffect
}
