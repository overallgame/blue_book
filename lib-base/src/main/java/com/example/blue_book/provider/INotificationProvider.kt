package com.example.blue_book.provider

/**
 * 通知服务接口 — 由 feature-message 模块提供（主界面消息角标等场景使用）
 */
interface INotificationProvider {

	/** 未读通知数（未登录返回 0） */
	suspend fun unreadCount(): Result<Long>
}
