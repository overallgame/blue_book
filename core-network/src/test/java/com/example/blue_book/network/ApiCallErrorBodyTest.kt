package com.example.blue_book.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * 非 2xx 时 **HTTP 状态码与业务码都要拿得到**。
 *
 * 起因是一个真实缺陷：`httpFailure` 只把 HTTP 状态码放进 `NetworkException.code`，
 * 业务码仅被用来当文案，于是"400 + `{"code":13006}`"和"400 + `{"code":13005}`"在客户端
 * 完全无法区分——而这两者处置相反（前者要重传该分片，后者重试无用）。
 */
class ApiCallErrorBodyTest {

	private fun errorResponse(status: Int, body: String): Response<Unit> =
		Response.error(status, body.toResponseBody("application/json".toMediaType()))

	@Test
	fun `business code is exposed alongside the http status`() {
		val failure = httpFailure(
			errorResponse(400, """{"code":13006,"message":"分片校验失败，请重传该分片","ttl":0,"data":null}""")
		)

		assertEquals("HTTP 状态码照旧放在 code", 400, failure.code)
		assertEquals("业务码要能被读到", 13006, failure.businessCode)
		assertEquals("中文文案要透出", "分片校验失败，请重传该分片", failure.message)
	}

	@Test
	fun `a different business code under the same status stays distinguishable`() {
		val mismatch = httpFailure(errorResponse(400, """{"code":13006,"message":"a"}"""))
		val badParams = httpFailure(errorResponse(400, """{"code":13005,"message":"b"}"""))

		assertEquals(13006, mismatch.businessCode)
		assertEquals(13005, badParams.businessCode)
	}

	@Test
	fun `an error body without a business code still yields a readable failure`() {
		val failure = httpFailure(errorResponse(500, "<html>gateway error</html>"))

		assertEquals(500, failure.code)
		assertEquals("解析不出业务码时为 null，而不是编一个", null, failure.businessCode)
		assertEquals("没有可解析的文案时用兜底文案", "服务器繁忙，请稍后重试", failure.message)
	}
}
