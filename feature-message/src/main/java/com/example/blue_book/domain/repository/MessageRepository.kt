package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.NotificationPage

/**
 * 消息中心的数据入口。
 *
 * 存在的意义是让 ViewModel 只依赖接口：消息页的编排（游标分页、乐观已读/删除、未读数）
 * 因此可以在纯 JVM 上测，而不必构造一个需要 `ApiGateway`（进而需要 Android Context）的数据源。
 */
interface MessageRepository {

	suspend fun list(cursorId: Long?, size: Int?): Result<NotificationPage>

	/** 全局未读数（含未加载的分页） */
	suspend fun unreadCount(): Result<Long>

	suspend fun markRead(id: Long): Result<Unit>

	suspend fun delete(id: Long): Result<Unit>

	suspend fun clearAll(): Result<Unit>
}
