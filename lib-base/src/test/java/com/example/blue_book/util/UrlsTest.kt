package com.example.blue_book.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [absoluteUrl] 的单测。
 *
 * 它错了的表现很隐蔽：不崩、不报错，只是图不显示（URL 拼出 `/` 或 `//`、或少了 host），
 * 所以三种输入形态各钉一条。
 */
class UrlsTest {

	private val base = "http://192.168.17.128:8080"

	@Test
	fun `joins path with leading slash`() {
		assertEquals("$base/hls/a.jpg", absoluteUrl(base, "/hls/a.jpg"))
	}

	@Test
	fun `adds slash when path has none`() {
		assertEquals("$base/hls/a.jpg", absoluteUrl(base, "hls/a.jpg"))
	}

	@Test
	fun `tolerates trailing slash on base url`() {
		// JUnit 4 的签名是 assertEquals(message, expected, actual)——消息在**第一个**，
		// 与 JUnit 5（消息在最后）相反。写反了不会编译失败（Kotlin 会把三个参数依次
		// 绑成 message/expected/actual），于是断言看着通过、报错信息里却是拿消息当实际值。
		assertEquals(
			"BASE_URL 尾部斜杠不该拼出双斜杠",
			"$base/hls/a.jpg", absoluteUrl("$base/", "/hls/a.jpg")
		)
	}

	@Test
	fun `keeps already absolute url untouched`() {
		assertEquals(
			"已经是绝对地址时必须原样返回（换 host 会指向错误的地方）",
			"https://cdn.example.com/a.jpg", absoluteUrl(base, "https://cdn.example.com/a.jpg")
		)
		assertEquals("http://cdn.example.com/a.jpg", absoluteUrl(base, "http://cdn.example.com/a.jpg"))
	}

	@Test
	fun `returns null for null blank and whitespace`() {
		assertNull(absoluteUrl(base, null))
		assertNull(absoluteUrl(base, ""))
		assertNull(absoluteUrl(base, "   "))
	}

	@Test
	fun `trims surrounding whitespace`() {
		assertEquals("服务端字段偶发带空白应当容忍", "$base/hls/a.jpg", absoluteUrl(base, "  /hls/a.jpg  "))
	}
}
