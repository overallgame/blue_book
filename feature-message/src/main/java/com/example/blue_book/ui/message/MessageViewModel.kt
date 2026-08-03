package com.example.blue_book.ui.message

import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
) : UdfViewModel<MessageIntent, MessageUiState, MessageEffect>(MessageUiState()) {

	override suspend fun handleIntent(intent: MessageIntent) {
		when (intent) {
			MessageIntent.Init -> initMessages()
			MessageIntent.Refresh -> refresh()
			MessageIntent.LoadMore -> loadMore()
			is MessageIntent.MarkRead -> markRead(intent.id)
		}
	}

	private suspend fun initMessages() {
		setState { copy(isLoading = false, isEmpty = false, unreadCount = 3, items = mockMessages()) }
	}

	private suspend fun refresh() {
		setState { copy(isLoading = false, items = mockMessages(), unreadCount = 3) }
	}

	private suspend fun loadMore() {
		// 后端 API 就绪后接入分页
	}

	private fun markRead(id: Long) {
		setState {
			val updated = items.map { if (it.id == id) it.copy(isRead = true) else it }
			copy(items = updated, unreadCount = updated.count { !it.isRead })
		}
	}

	/** 本地 mock 数据，后端 API 就绪后替换 */
	private fun mockMessages() = listOf(
		MessageItem(1, MessageType.Follow, nickname = "摄影达人", content = "关注了你", time = System.currentTimeMillis() - 60_000),
		MessageItem(2, MessageType.Like, nickname = "旅行者小张", content = "赞了你的视频", time = System.currentTimeMillis() - 300_000, thumbUrl = "thumb"),
		MessageItem(3, MessageType.Comment, nickname = "美食家小王", content = "评论了你的视频：这个地方我也去过！", time = System.currentTimeMillis() - 3_600_000),
		MessageItem(4, MessageType.System, content = "欢迎来到小蓝书，开始你的创作之旅吧", time = System.currentTimeMillis() - 86_400_000, isRead = true),
		MessageItem(5, MessageType.Like, nickname = "音乐爱好者", content = "赞了你的视频", time = System.currentTimeMillis() - 172_800_000, isRead = true),
		MessageItem(6, MessageType.Follow, nickname = "画师小林", content = "关注了你", time = System.currentTimeMillis() - 259_200_000, isRead = true),
		MessageItem(7, MessageType.Comment, nickname = "程序员老李", content = "评论了你的视频：技术干货，收藏了", time = System.currentTimeMillis() - 604_800_000, isRead = true)
	)
}
