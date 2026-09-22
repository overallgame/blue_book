package com.example.bluebook.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 服务端码格式判定的**纯单测**（不起 Spring）。
 *
 * 与客户端 `lib-base/.../scan/ScanCodeFormatTest.kt` 是同一组用例的两个实现——
 * 两边行为必须一致，所以这里刻意把同样的恶意输入再列一遍：
 * 服务端这份是**最终判据**，它漏了一个洞，客户端那层的白名单就形同虚设。
 */
class ScanCodeFormatTest {

	private fun video(payload: String) = ScanCodeFormat.parse(payload) as? ScanCode.Video
	private fun user(payload: String) = ScanCodeFormat.parse(payload) as? ScanCode.User

	// ───────────── 正常路径 ─────────────

	@Test
	fun `parses video code`() {
		assertEquals(42L, video("https://${ScanCodeFormat.CODE_HOST}/v/42")?.aid, "标准视频码应当解出 aid")
	}

	@Test
	fun `parses user code`() {
		assertEquals(7L, user("https://${ScanCodeFormat.CODE_HOST}/u/7")?.id, "标准用户码应当解出 id")
	}

	@Test
	fun `tolerates trailing slash query and fragment`() {
		assertEquals(1L, video("https://${ScanCodeFormat.CODE_HOST}/v/1/")?.aid, "末尾斜杠应当容忍")
		assertEquals(1L, video("https://${ScanCodeFormat.CODE_HOST}/v/1?from=wechat")?.aid, "query 应当忽略")
		assertEquals(1L, video("https://${ScanCodeFormat.CODE_HOST}/v/1#top")?.aid, "fragment 应当忽略")
	}

	@Test
	fun `tolerates surrounding whitespace and uppercase host`() {
		assertEquals(1L, video("  https://${ScanCodeFormat.CODE_HOST}/v/1  ")?.aid, "两端空白应当容忍")
		assertEquals(1L, video("https://${ScanCodeFormat.CODE_HOST.uppercase()}/v/1")?.aid, "host 大小写应当容忍")
	}

	@Test
	fun `accepts plain http as well`() {
		// 生成侧只产 https，但解析侧与客户端保持一致地接受 http：宽入严出，
		// 免得"同一个 host 换成 http 就扫不出来了"这种说不清的差异
		assertEquals(1L, video("http://${ScanCodeFormat.CODE_HOST}/v/1")?.aid, "http 应当同样接受")
	}

	@Test
	fun `accepts explicit port`() {
		assertEquals(1L, video("https://${ScanCodeFormat.CODE_HOST}:443/v/1")?.aid, "显式端口应当容忍")
	}

	// ───────────── 安全边界：冒充本站的 host ─────────────

	@Test
	fun `rejects lookalike hosts`() {
		// 这些是最危险的一类：用户一眼看不出区别，而它们指向别人的服务器
		assertNull(
			ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}.evil.com/v/1"),
			"后缀追加的相似域名必须判为非本站码"
		)
		assertNull(
			ScanCodeFormat.parse("https://a.${ScanCodeFormat.CODE_HOST}/v/1"),
			"子域必须判为非本站码（白名单精确匹配，不做后缀匹配）"
		)
		assertNull(
			ScanCodeFormat.parse("https://evil.com/${ScanCodeFormat.CODE_HOST}/v/1"),
			"host 出现在路径里不算"
		)
		assertNull(
			ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}.invalid/v/1"),
			"多一段 TLD 也不算"
		)
	}

	@Test
	fun `rejects userinfo spoofing`() {
		// 浏览器/人会读成"是 bluebook.invalid"，但真正的 host 是 evil.com
		assertNull(
			ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}@evil.com/v/1"),
			"userinfo 里塞本站 host 必须判为非本站码"
		)
	}

	// ───────────── 安全边界：非 http(s) scheme ─────────────

	@Test
	fun `rejects non http schemes`() {
		listOf(
			"javascript:alert(1)",
			"file:///etc/passwd",
			"intent://scan/#Intent;scheme=zxing;end",
			"data:text/html;base64,PHNjcmlwdD4=",
			"content://com.example/secret",
			"bluebook.invalid/v/1",
			"//${ScanCodeFormat.CODE_HOST}/v/1"
		).forEach { payload ->
			assertNull(ScanCodeFormat.parse(payload), "非 http(s) 输入必须判为非本站码：$payload")
		}
	}

	// ───────────── 路径与 id 的边界 ─────────────

	@Test
	fun `rejects unknown paths`() {
		listOf(
			"https://${ScanCodeFormat.CODE_HOST}/t/1",
			"https://${ScanCodeFormat.CODE_HOST}/v/",
			"https://${ScanCodeFormat.CODE_HOST}/v/abc",
			"https://${ScanCodeFormat.CODE_HOST}/v/-1",
			"https://${ScanCodeFormat.CODE_HOST}/v/1/2",
			"https://${ScanCodeFormat.CODE_HOST}/V/1",
			"https://${ScanCodeFormat.CODE_HOST}/",
			"https://${ScanCodeFormat.CODE_HOST}"
		).forEach { payload ->
			assertNull(ScanCodeFormat.parse(payload), "路径结构不符合契约必须判为非本站码：$payload")
		}
	}

	@Test
	fun `rejects login ticket path which belongs to phase 2`() {
		// /qr/{ticket} 是 Phase 2 的登录码，不走本接口（设计方案 8.2）。
		// 客户端认得它，服务端刻意不认——落到这里说明两端版本不一致。
		assertNull(
			ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}/qr/abc123"),
			"登录码不走 resolve，服务端不认它"
		)
	}

	@Test
	fun `rejects zero id`() {
		assertNull(ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}/v/0"), "aid 必须为正数")
		assertNull(ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}/u/0"), "id 必须为正数")
	}

	@Test
	fun `rejects overlong numeric id without throwing`() {
		// 2000 位数字：用 toLong() 会抛 NumberFormatException 而被兜成 500。
		// 畸形输入绝不该表现为"服务端故障"（这条正是 handleBadRequest 的注释里说的问题）。
		val huge = "9".repeat(2000)
		assertNull(ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}/v/$huge"), "超长数字 id 应当判为非本站码")
	}

	@Test
	fun `rejects blank and overlong payload`() {
		assertNull(ScanCodeFormat.parse(""), "空串不是本站码")
		assertNull(ScanCodeFormat.parse("   "), "纯空白不是本站码")
		assertNull(
			ScanCodeFormat.parse("https://${ScanCodeFormat.CODE_HOST}/v/1" + "x".repeat(2048)),
			"超过长度上限的 payload 直接判为非本站码"
		)
	}

	@Test
	fun `does not parse arbitrary text`() {
		listOf("hello world", "WIFI:S:home;T:WPA;P:123456;;", "13800138000", "SMSTO:10086:hi")
			.forEach { assertNull(ScanCodeFormat.parse(it), "普通文本不该被认成站内码：$it") }
	}

	// ───────────── 「编」「解」必须互为逆 ─────────────

	@Test
	fun `generated urls round trip`() {
		val aid = 12345L
		val uid = 678L
		assertEquals(aid, video(ScanCodeFormat.videoUrl(aid))?.aid, "videoUrl 生成的码必须能解回原 aid")
		assertEquals(uid, user(ScanCodeFormat.userUrl(uid))?.id, "userUrl 生成的码必须能解回原 id")
		assertTrue(
			ScanCodeFormat.videoUrl(aid).startsWith("https://"),
			"生成的码必须是 https（对外可分享）"
		)
	}

	@Test
	fun `code host is the reserved invalid tld`() {
		// 本期的前提（设计方案 8.1 P1）：码域名不可达，用 RFC 2606 保留域占位。
		// 一旦换成真域名，这条会失败——那是**预期内的提醒**：改 host 要同步
		// ① 客户端 ScanCodeFormat ② tools/check_scan_code_contract.py ③ 设计方案 8.1/6.1
		assertEquals("bluebook.invalid", ScanCodeFormat.CODE_HOST, "码域名变更是契约变更，必须显式确认")
	}
}
