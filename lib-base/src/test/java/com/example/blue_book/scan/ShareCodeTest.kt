package com.example.blue_book.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分享文案的单测（R7 的"发出去的码必须能扫回来"那一半）。
 *
 * 这里是**生成侧**。判据只有一条且很硬：**文案里那段链接必须逐字等于
 * `ScanCodeFormat` 生成的站内码**——不是"看起来像个链接"，也不是"拼得差不多"。
 *
 * 为什么这条必须测：如果这里手拼字符串（`"https://.../v/$aid"`），那么"改码格式"
 * 就要改两处，而漏掉一处的表现是——**用户分享出去的码，自己扫不出来**，
 * 且只有等到别人真的扫了才发现。这正是设计方案 4.1 把编解码收敛到 `ScanCodeFormat`
 * 的原因，本文件是它的验收。
 */
class ShareCodeTest {

	@Test
	fun `video text contains the canonical code verbatim`() {
		val text = ShareCode.video(title = "标题", aid = 123L)

		assertTrue(
			"文案里必须**原样**含有 ScanCodeFormat 生成的码，不能另拼一个",
			text.contains(ScanCodeFormat.videoUrl(123L))
		)
	}

	@Test
	fun `user text contains the canonical code verbatim`() {
		val text = ShareCode.user(nickname = "小明", id = 7L)

		assertTrue(
			"文案里必须原样含有 ScanCodeFormat 生成的码",
			text.contains(ScanCodeFormat.userUrl(7L))
		)
	}

	@Test
	fun `shared code parses back into an internal code`() {
		// 闭环的最后一段（客户端可达范围内）：**从文案里把那段 URL 抠出来**再解析，
		// 仍应是"站内码"且 id 不丢。真正端到端还差服务端 resolve，
		// 但"自己分享的码自己认不出来"这类问题到这里就能拦住。
		//
		// 按形状抠（https?://非空白字符）而不是按固定串匹配：如果文案里被手拼成
		// 另一段 URL，这里抠出来的就是那段错的，照样会被下面的断言抓住
		val url = Regex("https?://\\S+")
			.find(ShareCode.video(title = "标题", aid = 999L))
			?.value
			?: throw AssertionError("分享文案里没有链接")

		val parsed = ScanCodeFormat.parse(url)
		assertTrue("分享出去的码必须被判为站内码，实际：$parsed", parsed is ScanTarget.InternalCode)
		assertEquals(999L, videoIdOf(parsed as ScanTarget.InternalCode))
	}

	@Test
	fun `text carries the app hint because the link is not openable yet`() {
		// P1（无域名）的直接后果：链接在浏览器里打不开，所以必须告诉对方"用小蓝书扫"。
		// 有了真域名与网页端后这条会失败——那是**预期内的提醒**：
		// 改 ShareCode.OPEN_HINT，并回来改这条用例
		val text = ShareCode.video(title = "标题", aid = 1L)

		assertTrue("缺了这句，收码的人会以为链接能直接打开", text.contains("扫一扫"))
	}

	@Test
	fun `title and nickname are included for context`() {
		assertTrue(ShareCode.video("这是一个标题", 1L).contains("这是一个标题"))
		assertTrue(ShareCode.user("小明", 1L).contains("小明"))
	}

	@Test
	fun `blank title degrades instead of producing broken wording`() {
		// "《》"或空行看起来像功能坏了；宁可给一句通用说法
		listOf("", "   ").forEach { blank ->
			val text = ShareCode.video(title = blank, aid = 1L)
			assertTrue("空标题不该出现空名字行，实际：$text", text.lineSequence().first().isNotBlank())
			assertTrue("链接还是要给", text.contains(ScanCodeFormat.videoUrl(1L)))
		}
		assertTrue(
			"空昵称同理",
			ShareCode.user("  ", 1L).lineSequence().first().isNotBlank()
		)
	}

	@Test
	fun `url ends its own line so chat apps can linkify it`() {
		// 混在句子中间时聊天应用常常只当成普通文字；单独成行（行尾）才会被识别成链接
		val lines = ShareCode.video("标题", 1L).lines()

		assertEquals("应当只有名字与链接两行", 2, lines.size)
		assertTrue(
			"链接要在行尾，前面只允许有提示语",
			lines[1].endsWith(ScanCodeFormat.videoUrl(1L))
		)
	}

	/** 从站内码里取出 id，只用于断言；生产侧不做这种解析（判据在服务端，见设计方案 6.2） */
	private fun videoIdOf(code: ScanTarget.InternalCode): Long =
		code.raw.substringAfterLast('/').toLong()
}
