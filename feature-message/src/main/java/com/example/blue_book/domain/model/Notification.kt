package com.example.blue_book.domain.model

/**
 * 一条通知。
 *
 * [type] 保留服务端的原值（`FOLLOW`/`LIKE`/…），由 UI 层决定怎么展示——
 * 领域层目前不建模"通知种类"，因为除展示外没有任何逻辑依赖它。
 */
data class Notification(
	val id: Long,
	val type: String,
	val senderId: Long,
	/** 已拼成绝对地址（服务端下发相对路径） */
	val avatar: String,
	val nickname: String,
	val content: String,
	val time: Long,
	val isRead: Boolean,
	val videoId: Long? = null
)

/** 一页通知：`hasMore` 决定还要不要续拉 */
data class NotificationPage(
	val items: List<Notification>,
	val hasMore: Boolean
)
