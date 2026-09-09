package com.example.blue_book.domain.model

import com.example.blue_book.data.UserAccount

/** 关注/粉丝列表分页结果（游标透传服务端语义） */
data class FollowListPage(
	val users: List<UserAccount> = emptyList(),
	val nextCursorId: Long? = null,
	val hasMore: Boolean = false
)
