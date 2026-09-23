package com.example.blue_book.data.mapper

import com.example.blue_book.data.dto.NotificationDto
import com.example.blue_book.data.dto.NotificationListDto
import com.example.blue_book.network.ApiGateway
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通知 DTO → domain 的映射。
 *
 * 重点钉住**头像绝对化**：服务端下发的是相对路径，不拼 host 交给 Glide 会一直加载失败
 * （表现为消息列表头像空着，而日志里什么都没有），这条以前是漏的。
 */
class NotificationMappersTest {

	private val base = ApiGateway.BASE_URL.trimEnd('/')

	@Test
	fun `relative avatar is turned into an absolute url`() {
		val item = NotificationDto(id = 1, senderAvatar = "/upload/images/a.jpg").toDomain()

		assertEquals("$base/upload/images/a.jpg", item.avatar)
	}

	@Test
	fun `already absolute avatar is kept as is`() {
		val item = NotificationDto(id = 1, senderAvatar = "https://cdn.example.com/a.jpg").toDomain()

		assertEquals("https://cdn.example.com/a.jpg", item.avatar)
	}

	@Test
	fun `missing avatar becomes empty string not a broken url`() {
		assertEquals("", NotificationDto(id = 1, senderAvatar = "").toDomain().avatar)
		assertEquals("", NotificationDto(id = 1, senderAvatar = "  ").toDomain().avatar)
	}

	@Test
	fun `page mapping keeps items and hasMore`() {
		val page = NotificationListDto(
			items = listOf(NotificationDto(id = 1), NotificationDto(id = 2)),
			hasMore = true
		).toDomain()

		assertEquals("条数应与 DTO 一致", 2, page.items.size)
		assertEquals("hasMore 要透传（决定还要不要续拉）", true, page.hasMore)
	}
}
