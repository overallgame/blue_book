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

	/** 加载更多失败是否已提示过（成功时复位），避免一次失败弹一串 toast */
	private var loadMoreErrorNotified = false

	override suspend fun handleIntent(intent: MessageIntent) {
		when (intent) {
			MessageIntent.Init -> refresh()
			MessageIntent.Refresh -> refresh()
			MessageIntent.LoadMore -> loadMore()
			is MessageIntent.MarkRead -> markRead(intent.id)
			is MessageIntent.Delete -> delete(intent.id)
			MessageIntent.ClearAll -> clearAll()
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
				val msg = e.message ?: "加载失败"
				setState { copy(isLoading = false, message = msg) }
				// 列表非空时页面不会显示空态，刷新失败必须靠提示，否则只有转圈停下、毫无反馈
				if (uiState.value.items.isNotEmpty()) sendEffect(MessageEffect.ShowToast(msg))
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
				loadMoreErrorNotified = false
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
				// 加载更多失败会导致列表在底部被静默截断，必须提示。
				// 但加去重：失败后 hasMore 仍为 true，列表短时每次滑动都会重试，
				// 不去重会变成一次失败弹一串 toast；成功后复位
				if (!loadMoreErrorNotified) {
					loadMoreErrorNotified = true
					sendEffect(MessageEffect.ShowToast(e.message ?: "加载失败"))
				}
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

	/** 删除单条：乐观移除，失败回滚并提示 */
	private suspend fun delete(id: Long) {
		val previous = uiState.value
		val target = previous.items.firstOrNull { it.id == id } ?: return
		setState {
			copy(
				items = items.filterNot { it.id == id },
				isEmpty = items.size <= 1,
				unreadCount = (unreadCount - if (target.isRead) 0 else 1).coerceAtLeast(0)
			)
		}
		remote.delete(id).onFailure { e ->
			setState { copy(items = previous.items, isEmpty = previous.isEmpty, unreadCount = previous.unreadCount) }
			sendEffect(MessageEffect.ShowToast(e.message ?: "删除失败"))
		}
	}

	/** 清空全部：乐观清空，失败回滚并提示 */
	private suspend fun clearAll() {
		val previous = uiState.value
		if (previous.items.isEmpty()) return
		setState { copy(items = emptyList(), isEmpty = true, unreadCount = 0, cursorId = null, hasMore = true) }
		remote.clearAll().onFailure { e ->
			setState {
				copy(items = previous.items, isEmpty = previous.isEmpty, unreadCount = previous.unreadCount)
			}
			sendEffect(MessageEffect.ShowToast(e.message ?: "清空失败"))
		}
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
