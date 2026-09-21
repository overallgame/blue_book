package com.example.blue_book.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ScanCodeFormat` 的单元测试。纯 JVM，无设备、无后端。
 *
 * 这是扫码的**安全边界**：`parse()` 的输入是完全不可信的外部数据（任何人都能生成任意二维码），
 * 所以本文件的重心不是 happy path，而是**相似域名、伪装 host、危险 scheme、畸形输入**这几类。
 *
 * 命名约定同项目其余测试：方法名英文、断言消息中文（JUnit 4 没有 `@DisplayName`，
 * 失败时真正被读到的是断言消息）。
 */
class ScanCodeFormatTest {

	// ───────────────────────── 生成 ─────────────────────────

	@Test
	fun videoUrlAndUserUrlMatchTheDocumentedFormat() {
		assertEquals("视频码格式", "https://bluebook.invalid/v/123", ScanCodeFormat.videoUrl(123))
		assertEquals("用户码格式", "https://bluebook.invalid/u/456", ScanCodeFormat.userUrl(456))
	}

	@Test
	fun generatedUrlsRoundTripThroughParse() {
		// 生成与解析必须自洽——这是 R7（分享产出链接）与 R1（扫一扫识别）能对上的前提。
		// 若哪天有人只改了一侧，这条会立刻失败。
		val video = ScanCodeFormat.videoUrl(123)
		val user = ScanCodeFormat.userUrl(456)
		assertEquals(
			"自己生成的视频链接必须被自己识别为站内码，且原文不改动",
			ScanTarget.InternalCode(video), ScanCodeFormat.parse(video)
		)
		assertEquals(
			"自己生成的用户链接同理",
			ScanTarget.InternalCode(user), ScanCodeFormat.parse(user)
		)
	}

	@Test
	fun buildRejectsNonPositiveId() {
		assertThrows("aid=0 应抛异常，而不是生成一个无效的码", IllegalArgumentException::class.java) {
			ScanCodeFormat.videoUrl(0)
		}
		assertThrows("用户 id 为负数同理", IllegalArgumentException::class.java) {
			ScanCodeFormat.userUrl(-1)
		}
	}

	// ───────────────────── 自家码的等价写法 ─────────────────────

	@Test
	fun parseRecognizesOwnCodes() {
		assertEquals(
			"自家视频码",
			ScanTarget.InternalCode("https://bluebook.invalid/v/123"),
			ScanCodeFormat.parse("https://bluebook.invalid/v/123")
		)
		assertEquals(
			"自家用户码",
			ScanTarget.InternalCode("https://bluebook.invalid/u/456"),
			ScanCodeFormat.parse("https://bluebook.invalid/u/456")
		)
	}

	@Test
	fun parseIsCaseInsensitiveOnSchemeAndHost() {
		// scheme 与 host 按 RFC 都不区分大小写
		assertTrue(
			"大写 scheme 与混合大小写 host 都要认得",
			ScanCodeFormat.parse("HTTPS://BlueBook.Invalid/v/1") is ScanTarget.InternalCode
		)
	}

	@Test
	fun parseAcceptsPlainHttpOnOwnHost() {
		// 当前后端是明文 HTTP；白名单只约束 host、不约束 scheme，
		// 这样将来切 https 时，已发出的 http 老码也不会失效
		assertTrue(
			"自家 host 的 http 链接同样是站内码",
			ScanCodeFormat.parse("http://bluebook.invalid/v/1") is ScanTarget.InternalCode
		)
	}

	@Test
	fun parseAcceptsTrailingSlashQueryAndFragment() {
		listOf(
			"https://bluebook.invalid/v/1/",
			"https://bluebook.invalid/v/1?from=wx",
			"https://bluebook.invalid/v/1#top",
		).forEach { payload ->
			assertTrue(
				"等价写法都要认得（末尾斜杠 / query / fragment）：$payload",
				ScanCodeFormat.parse(payload) is ScanTarget.InternalCode
			)
		}
	}

	@Test
	fun parseIgnoresPortOnOwnHost() {
		// 白名单只约束 host：码不会被真的请求，端口没有安全含义；
		// 忽略端口也避免将来换部署端口时老码失效
		assertTrue(
			"带端口的自家链接仍算站内码",
			ScanCodeFormat.parse("https://bluebook.invalid:8443/v/1") is ScanTarget.InternalCode
		)
	}

	@Test
	fun parseTrimsSurroundingWhitespace() {
		assertEquals(
			"首尾空白应裁掉后再归类（有些编码器会带上）",
			ScanTarget.InternalCode("https://bluebook.invalid/v/123"),
			ScanCodeFormat.parse("  https://bluebook.invalid/v/123  ")
		)
	}

	@Test
	fun parseSendsUnknownPathsOnOwnHostToServer() {
		// 本地不校验路径结构：新格式的码交给服务端裁定，老版本客户端也能正确工作。
		// 这与「resolve 传原始字符串、让服务端当唯一裁判」是同一个思路。
		assertTrue(
			"自家 host 上不认识的路径也交给服务端，而不是本地判为非法",
			ScanCodeFormat.parse("https://bluebook.invalid/t/999") is ScanTarget.InternalCode
		)
	}

	// ───────────────────── 安全：伪装与相似域名 ─────────────────────

	@Test
	fun parseRejectsLookalikeHosts() {
		listOf(
			"https://bluebook.invalid.evil.com/v/1",
			"https://evil-bluebook.invalid/v/1",
			"https://a.bluebook.invalid/v/1",
			"https://bluebook.invalidx/v/1",
		).forEach { payload ->
			assertTrue(
				"host 必须精确命中白名单，不做子域/后缀匹配——相似域名一律外部链接：$payload",
				ScanCodeFormat.parse(payload) is ScanTarget.ExternalUrl
			)
		}
	}

	@Test
	fun parseRejectsHostHiddenInUserInfo() {
		// URL 中 @ 之前的 userinfo 常被用来伪装 host：真实 host 是 evil.com
		assertTrue(
			"userinfo 里出现的白名单 host 不算命中（真实 host 是 evil.com）",
			ScanCodeFormat.parse("https://bluebook.invalid@evil.com/v/1") is ScanTarget.ExternalUrl
		)
	}

	@Test
	fun parseTreatsDeploymentAddressAsExternal() {
		// 回归用例：码里的 host 与部署地址**刻意解耦**。若有人把它改回读 BASE_URL，
		// 这条会立刻失败——那会让内网地址被印进二维码，且换环境后所有码失效。
		assertTrue(
			"内网部署地址不是站内码 host",
			ScanCodeFormat.parse("http://192.168.17.128:8080/v/1") is ScanTarget.ExternalUrl
		)
	}

	@Test
	fun parseNeverClassifiesNonHttpSchemesAsOpenable() {
		// 最关键的一条：非 http(s) 绝不能落到 ExternalUrl，否则 UI 会为它提供
		// 「用浏览器打开」的入口 —— 那就是"扫码即执行"。
		listOf(
			"javascript:alert(1)",
			"file:///data/data/com.example.blue_book/databases/app.db",
			"intent://scan/#Intent;scheme=zxing;end",
			"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
			"content://com.example.blue_book.provider/secret",
		).forEach { payload ->
			assertTrue(
				"非 http(s) scheme 必须按纯文本处理，不能给它「打开」入口：$payload",
				ScanCodeFormat.parse(payload) is ScanTarget.PlainText
			)
		}
	}

	// ───────────────────────── 畸形输入 ─────────────────────────

	@Test
	fun parseTreatsBlankAsPlainText() {
		assertEquals("空串按纯文本处理，不抛异常", ScanTarget.PlainText(""), ScanCodeFormat.parse(""))
		assertEquals("纯空白同理", ScanTarget.PlainText("   "), ScanCodeFormat.parse("   "))
	}

	@Test
	fun parseTreatsProtocolRelativeAsPlainText() {
		// //host/path 没有 scheme，不能当成链接
		assertTrue(
			"协议相对写法没有 scheme，按纯文本处理",
			ScanCodeFormat.parse("//bluebook.invalid/v/1") is ScanTarget.PlainText
		)
	}

	@Test
	fun parseTreatsOverlongPayloadAsPlainText() {
		// 构造一个"看起来合法但超长"的串：证明拦下它的是长度上限，而不是恰好不匹配正则
		val padded = "https://bluebook.invalid/v/1?pad=" + "a".repeat(2100)
		assertTrue(
			"超过长度上限就不再解析（即使它看起来是合法站内码）",
			ScanCodeFormat.parse(padded) is ScanTarget.PlainText
		)
	}

	@Test
	fun parseKeepsUnrecognizedTextAsIs() {
		assertEquals(
			"无法归类的文本要原样保留（供展示与复制），不要 trim 掉用户的内容",
			ScanTarget.PlainText("  就是一段普通文字  "),
			ScanCodeFormat.parse("  就是一段普通文字  ")
		)
	}

	@Test
	fun parseTreatsMalformedUrlsAsPlainText() {
		listOf(
			"https://",
			"https:///v/1",
			"http://:8080/v/1",
			"这不是一个链接",
		).forEach { payload ->
			val target = ScanCodeFormat.parse(payload)
			assertTrue(
				"畸形输入不能抛异常、也不能被误判为站内码：$payload -> $target",
				target !is ScanTarget.InternalCode
			)
		}
	}

	@Test
	fun parseDefersBareOwnHostToServer() {
		// 只有域名没有路径。**刻意不在这里判它非法**：按「让服务端当唯一裁判」的原则，
		// host 命中了就交上去，由服务端给出"这不是小蓝书的二维码"。
		// 代价是一次无谓的请求；收益是后端将来加新格式时本地不需要跟着改。
		assertEquals(
			"自家 host 但无路径：交给服务端裁定，而不是本地判非法",
			ScanTarget.InternalCode("https://bluebook.invalid"),
			ScanCodeFormat.parse("https://bluebook.invalid")
		)
	}

	// ───────────────────────── 登录码（Phase 2）─────────────────────────

	@Test
	fun parseRecognizesLoginTicket() {
		assertEquals(
			"登录码要本地认出来——它不走 /scan/resolve",
			ScanTarget.LoginTicket("AbC-123_x.y~z"),
			ScanCodeFormat.parse("https://bluebook.invalid/qr/AbC-123_x.y~z")
		)
	}

	@Test
	fun parseTreatsEmptyTicketAsInternalCode() {
		listOf("https://bluebook.invalid/qr/", "https://bluebook.invalid/qr//").forEach { payload ->
			assertTrue(
				"没有票据的空路径不构成登录码，交给服务端判：$payload",
				ScanCodeFormat.parse(payload) is ScanTarget.InternalCode
			)
		}
	}
}
