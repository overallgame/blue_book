package com.example.blue_book.data.mapper

import com.example.blue_book.data.remote.dto.ScanResolveDto
import com.example.blue_book.domain.model.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DTO → domain 的"把门"逻辑单测。
 *
 * 这里是**唯一**做字段完整性判断的地方（过了这层，下游就都不用判空），
 * 所以用例集中在"什么该被拦下"与"什么只是不好看、不该被拦下"。
 */
class ScanMappersTest {

	private val base = "http://192.168.17.128:8080"

	@Test
	fun `maps video dto to domain`() {
		val content = ScanResolveDto(
			type = "VIDEO",
			targetId = 42L,
			title = "标题",
			subtitle = "作者",
			cover = "/hls/a.jpg"
		).toScannedContent(base)

		assertEquals(ContentKind.VIDEO, content?.kind)
		assertEquals(42L, content?.targetId)
		assertEquals("标题", content?.title)
		assertEquals("作者", content?.subtitle)
		// 服务端下发相对路径，host 由客户端补——这条与 /videos/*/dto 的行为必须一致
		assertEquals("$base/hls/a.jpg", content?.cover)
	}

	@Test
	fun `maps user dto to domain`() {
		val content = ScanResolveDto(
			type = "USER",
			targetId = 7L,
			title = "昵称",
			cover = "/upload/images/a.jpg"
		).toScannedContent(base)

		assertEquals(ContentKind.USER, content?.kind)
		assertNull("没给副标题就是 null，不该变成空串", content?.subtitle)
	}

	@Test
	fun `accepts type name case insensitively`() {
		// 后端的枚举序列化风格可能变（VIDEO / video），靠名字对应就还能工作
		assertEquals(
			ContentKind.VIDEO,
			ScanResolveDto(type = "video", targetId = 1L, title = "t").toScannedContent(base)?.kind
		)
		assertEquals(
			ContentKind.USER,
			ScanResolveDto(type = " User ", targetId = 1L, title = "t").toScannedContent(base)?.kind
		)
	}

	@Test
	fun `rejects unknown or missing type instead of guessing`() {
		// 后端将来加新码类型时，老客户端必须能明确说"暂不支持"，
		// 而不是猜一个目标把用户导航到错误的地方
		assertNull(
			"不认识的新类型必须映射失败",
			ScanResolveDto(type = "TOPIC", targetId = 1L, title = "t").toScannedContent(base)
		)
		assertNull(
			"缺失 type 必须映射失败",
			ScanResolveDto(targetId = 1L, title = "t").toScannedContent(base)
		)
	}

	@Test
	fun `rejects missing or non positive target id`() {
		assertNull(
			"缺 targetId 映射失败",
			ScanResolveDto(type = "VIDEO", title = "t").toScannedContent(base)
		)
		assertNull(
			"id <= 0 不是合法目标（生成侧 require(aid > 0)）",
			ScanResolveDto(type = "VIDEO", targetId = 0L, title = "t").toScannedContent(base)
		)
	}

	@Test
	fun `allows blank title as empty string not a failure`() {
		// 标题难看一点不该让用户扫不出这条码：能跳转比好看重要
		val content = ScanResolveDto(type = "VIDEO", targetId = 1L, title = "   ").toScannedContent(base)

		assertEquals("空白标题归一成空串，而不是映射失败", "", content?.title)
	}

	@Test
	fun `keeps absolute cover untouched`() {
		val content = ScanResolveDto(
			type = "VIDEO",
			targetId = 1L,
			title = "t",
			cover = "https://cdn.example.com/a.jpg"
		).toScannedContent(base)

		assertEquals("已经是绝对地址就不能再拼一遍 host", "https://cdn.example.com/a.jpg", content?.cover)
	}

	@Test
	fun `maps missing cover to null`() {
		assertNull(
			ScanResolveDto(type = "VIDEO", targetId = 1L, title = "t").toScannedContent(base)?.cover
		)
	}
}
