package com.example.blue_book.data.remote

import com.example.blue_book.data.mapper.toScannedContent
import com.example.blue_book.data.repository.toScanResolveFailure
import com.example.blue_book.domain.model.ContentKind
import com.example.blue_book.domain.repository.ScanResolveFailure
import com.example.blue_book.network.apiCall
import com.example.blue_book.scan.ScanCodeFormat
import com.example.blue_book.scan.ScanTarget
import com.example.blue_book.scan.ShareCode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * **数据层的真集成测试**：真 HTTP（MockWebServer）+ 真 Retrofit + 真 Gson + 真 `apiCall`。
 *
 * ## 为什么值得单独写一个（而不是只靠 FakeScanRepository 的 ViewModel 测试）
 *
 * `ScanViewModelTest` 用假仓库，跳过了整个 data 层。而 data 层里恰好是**最不可能靠肉眼发现**的东西：
 *
 * 1. `@GET("/api/v2/scan/resolve")` 的路径写对没有
 * 2. `@Query("payload")` 有没有把原始字符串**正确编码**——扫码内容里出现 `&`、`#`、`?`、空格、
 *    中文都是常态，手拼 URL 时这些正是会静默截断请求的地方（服务端只收到半个 payload，
 *    表现为"明明是对的码却说不是小蓝书的码"）
 * 3. 信封解析（`ApiResponse<T>` 经 GsonConverterFactory 的泛型反序列化）
 * 4. `apiCall` 的错误映射 → [ScanResolveFailure] 的分类（400/403/404 对 5xx 对连不上）
 *
 * 这四件都只在"真 HTTP"这一层才暴露；一旦错了，**在真机上只表现为一句莫名其妙的提示**。
 *
 * 用 MockWebServer 而不是 JDK 的 `com.sun.net.httpserver`：Android 单元测试的编译类路径
 * 以 android.jar 为基准，`com.sun.*` 不可见。
 */
class ScanApiIntegrationTest {

	private lateinit var server: MockWebServer
	private lateinit var api: ScanApi

	@Before
	fun setUp() {
		server = MockWebServer().apply { start() }
		api = Retrofit.Builder()
			.baseUrl(server.url("/"))
			.client(
				OkHttpClient.Builder()
					.connectTimeout(2, TimeUnit.SECONDS)
					.readTimeout(2, TimeUnit.SECONDS)
					.build()
			)
			.addConverterFactory(GsonConverterFactory.create())
			.build()
			.create(ScanApi::class.java)
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	// ───────────── 请求构造（路径 + 编码）─────────────

	@Test
	fun `calls the contract path`() = runBlocking {
		server.enqueue(MockResponse().setBody(okBody()))

		apiCall { api.resolve("https://bluebook.invalid/v/1") }

		val path = server.takeRequest().path.orEmpty()
		assertTrue(
			"请求路径必须是契约里的 /api/v2/scan/resolve，实际：$path",
			path.startsWith("/api/v2/scan/resolve?")
		)
	}

	@Test
	fun `url encodes payload containing query and fragment characters`() = runBlocking {
		server.enqueue(MockResponse().setBody(okBody()))
		// 这些是扫码内容里真实会出现的东西：query、fragment、`&`、空格、中文
		val payload = "https://bluebook.invalid/v/1?a=1&b=2#top 中文"

		apiCall { api.resolve(payload) }

		// takeRequest().path 拿到的是**未经解码的原始路径**，正是要的证据
		val rawQuery = server.takeRequest().path.orEmpty().substringAfter("?")
		assertEquals("编码正确时不应出现裸的 &（那会把一个参数切成两个）", 1, rawQuery.split("&").size)
		assertEquals("编码正确时不应出现裸的 #（那会把参数截断）", 0, rawQuery.count { it == '#' })
		assertEquals(
			"服务端解码后必须与原始字符串逐字相同——这条排除了「只收到半个 payload」",
			payload, URLDecoder.decode(rawQuery.removePrefix("payload="), "UTF-8")
		)
	}

	// ───────────── 成功路径 ─────────────

	@Test
	fun `parses success envelope into domain content`() = runBlocking {
		server.enqueue(MockResponse().setBody(okBody(type = "VIDEO", targetId = 42L, title = "扫到的视频")))

		val result = apiCall { api.resolve("https://bluebook.invalid/v/42") }

		val content = result.getOrThrow().toScannedContent("http://192.168.17.128:8080")
		assertEquals(ContentKind.VIDEO, content?.kind)
		assertEquals(42L, content?.targetId)
		assertEquals("扫到的视频", content?.title)
		assertEquals(
			"封面要拼成绝对地址（服务端下发相对路径）",
			"http://192.168.17.128:8080/hls/cover.jpg", content?.cover
		)
	}

	@Test
	fun `parses user envelope`() = runBlocking {
		server.enqueue(MockResponse().setBody(okBody(type = "USER", targetId = 7L, title = "昵称")))

		val content = apiCall { api.resolve("x") }.getOrThrow()
			.toScannedContent("http://host")

		assertEquals(ContentKind.USER, content?.kind)
		assertEquals(7L, content?.targetId)
	}

	// ───────────── R7 闭环：分享出去的码能扫回来 ─────────────

	@Test
	fun `code produced by the share text resolves into a jump target`() = runBlocking {
		// 这是"分享 → 扫一扫"的**闭环**（客户端可见的那一段）：
		// 分享文案 → 抠出链接 → 判为站内码 → 真 Retrofit 请求 → 得到跳转目标。
		//
		// 这条链跨了两个模块（feature-video 分享 / feature-scan 扫码），耦合**只有一个字符串**：
		// 两端各自测绿并不保证它们对得上，所以用一条用例钉住"分享出去的码扫得回来"。
		val shareText = ShareCode.video(title = "标题", aid = 42L)
		val code = Regex("https?://\\S+").find(shareText)?.value
			?: throw AssertionError("分享文案里没有链接：$shareText")

		// 第一道门：扫码页只会对"站内码"发 resolve（其余三类在本地处置）。
		// 分享产出的码若不在这里被认成站内码，后面一切都不会发生
		val target = ScanCodeFormat.parse(code)
		assertTrue("分享出来的码必须被判为站内码，实际：$target", target is ScanTarget.InternalCode)

		server.enqueue(MockResponse().setBody(okBody(type = "VIDEO", targetId = 42L, title = "标题")))
		val content = apiCall { api.resolve((target as ScanTarget.InternalCode).raw) }
			.getOrThrow()
			.toScannedContent("http://192.168.17.128:8080")

		assertEquals("闭环终点：拿到了该跳转的目标", 42L, content?.targetId)
		assertEquals(ContentKind.VIDEO, content?.kind)
		val rawQuery = server.takeRequest().path.orEmpty().substringAfter("payload=")
		assertEquals(
			"服务端必须收到**完整的**原始字符串（截断了就查不出正确的内容）",
			code, URLDecoder.decode(rawQuery, "UTF-8")
		)
	}

	// ───────────── 失败路径 → 分类（决定给不给「重试」）─────────────

	@Test
	fun `maps business code in 2xx body to rejected with server message`() = runBlocking {
		// 后端对"不是本站码"返回 HTTP 400 + 业务码 15001，客户端两处都能解析；
		// 这里测 **2xx + 业务码非 0** 这条分支：命中它时必须保留业务码自带的中文文案，
		// 不能退化成兜底的"数据解析错误"
		server.enqueue(
			MockResponse().setBody("""{"code":15001,"message":"这不是小蓝书的二维码","ttl":0,"data":null}""")
		)

		val failure = apiCall { api.resolve("x") }.exceptionOrNull()

		val classified = failure!!.toScanResolveFailure()
		assertTrue("不是本站码，重试无意义", classified is ScanResolveFailure.Rejected)
		assertEquals("这不是小蓝书的二维码", classified.message)
	}

	@Test
	fun `maps http 404 to rejected and keeps server message`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(404)
				.setBody("""{"code":15003,"message":"视频不存在或已被删除","ttl":0,"data":null}""")
		)

		val failure = apiCall { api.resolve("https://bluebook.invalid/v/9") }.exceptionOrNull()

		val classified = failure!!.toScanResolveFailure()
		assertTrue("内容不在了，重试还是不存在", classified is ScanResolveFailure.Rejected)
		// 错误体里的中文要透出，而不是客户端兜底的"请求失败(404)"
		assertEquals("视频不存在或已被删除", classified.message)
	}

	@Test
	fun `maps http 403 to rejected`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(403)
				.setBody("""{"code":15002,"message":"该内容暂时无法查看","ttl":0,"data":null}""")
		)

		val classified = apiCall { api.resolve("x") }.exceptionOrNull()!!.toScanResolveFailure()

		assertTrue("无权查看，重试无意义", classified is ScanResolveFailure.Rejected)
		assertEquals("该内容暂时无法查看", classified.message)
	}

	@Test
	fun `maps http 500 to retryable failure`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(500)
				.setBody("""{"code":14999,"message":"服务器繁忙，请稍后再试","ttl":0,"data":null}""")
		)

		val classified = apiCall { api.resolve("x") }.exceptionOrNull()!!.toScanResolveFailure()

		assertTrue("5xx 是服务端临时故障，应当可重试", classified is ScanResolveFailure.Network)
	}

	@Test
	fun `maps unreachable server to retryable failure`() = runBlocking {
		// 手机连不上后端网段时就是这一条：必须给"重试"，而不是一句"未知错误"且无路可走
		server.shutdown()

		val classified = apiCall { api.resolve("x") }.exceptionOrNull()!!.toScanResolveFailure()

		assertTrue("连不上必须可重试", classified is ScanResolveFailure.Network)
		// 文案由 NetworkException.from(ConnectException) 给出——不能是平台原始英文文本
		assertTrue(
			"文案要指向网络问题，实际：${classified.message}",
			classified.message.orEmpty().contains("网络")
		)
	}

	// ───────────────────────── 夹具 ─────────────────────────

	private fun okBody(
		type: String = "VIDEO",
		targetId: Long = 42L,
		title: String = "标题"
	): String = """
		{"code":0,"message":"success","ttl":0,"data":{
		  "type":"$type","targetId":$targetId,"title":"$title",
		  "subtitle":"作者","cover":"/hls/cover.jpg"}}
	""".trimIndent()
}
