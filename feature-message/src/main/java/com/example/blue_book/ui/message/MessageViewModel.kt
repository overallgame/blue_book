package com.example.blue_book.ui.message

import com.example.blue_book.data.dto.NotificationDto
import com.example.blue_book.data.remote.MessageRemoteDataSource
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 消息中心：真实通知数据（游标分页 + 单条已读 + 未读数） */
@HiltViewModel
class MessageViewModel @Inject constructor(
	private val remote: MessageRemoteDataSource
) : UdfViewModel<MessageIntent, MessageUiState, MessageEffect>(MessageUiState()) {

	override suspend fun handleIntent(intent: MessageIntent) {
		when (intent) {
			MessageIntent.Init -> refresh()
			MessageIntent.Refresh -> refresh()
			MessageIntent.LoadMore -> loadMore()
			is MessageIntent.MarkRead -> markRead(intent.id)
		}
	}

	private suspend fun refresh() {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null, cursorId = null, hasMore = true) } },
			call = { remote.list(cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { dto ->
				val items = dto.items.map { it.toUi() }
				setState {
					copy(
						items = items,
						isLoading = false,
						isEmpty = items.isEmpty(),
						unreadCount = items.count { !it.isRead },
						cursorId = items.lastOrNull()?.id,
						hasMore = dto.hasMore
					)
				}
				// 拉取全局未读数（含未加载分页）
				remote.unreadCount().onSuccess { count ->
					setState { copy(unreadCount = count.toInt()) }
				}
			},
			onFailure = { e ->
				setState { copy(isLoading = false, message = e.message ?: "加载失败") }
			}
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { remote.list(cursorId = state.cursorId, size = state.pageSize) },
			onSuccess = { dto ->
				val mapped = dto.items.map { it.toUi() }
				setState {
					copy(
						items = items + mapped,
						isLoading = false,
						cursorId = mapped.lastOrNull()?.id ?: cursorId,
						hasMore = dto.hasMore
					)
				}
			},
			onFailure = { e ->
				setState { copy(isLoading = false, message = e.message ?: "加载失败") }
			}
		)
	}

	/** 单条已读：乐观更新，接口失败静默（下次刷新自动纠正） */
	private suspend fun markRead(id: Long) {
		val target = uiState.value.items.firstOrNull { it.id == id } ?: return
		if (target.isRead) return
		setState {
			copy(
				items = items.map { if (it.id == id) it.copy(isRead = true) else it },
				unreadCount = (unreadCount - 1).coerceAtLeast(0)
			)
		}
		remote.markRead(id)
	}

	private fun NotificationDto.toUi(): MessageItem = MessageItem(
		id = id,
		type = when (type.uppercase()) {
			"FOLLOW" -> MessageType.Follow
			"LIKE" -> MessageType.Like
			"COLLECT" -> MessageType.Collect
			"COMMENT" -> MessageType.Comment
			else -> MessageType.System
		},
		senderId = senderId,
		avatar = senderAvatar,
		nickname = senderNickname,
		content = content,
		time = createdAt,
		isRead = isRead,
		videoId = videoId
	)
}
