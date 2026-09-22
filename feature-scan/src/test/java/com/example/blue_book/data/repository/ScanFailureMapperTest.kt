package com.example.blue_book.data.repository

import com.example.blue_book.domain.repository.ScanResolveFailure
import com.example.blue_book.network.exception.NetworkException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "给不给重试"的判据穷举。
 *
 * 这个分支是失败矩阵里唯一**由代码而不是文案**决定出路的地方（设计方案 7.2）：
 * 判错的表现不是崩溃，而是**用户按了重试却永远失败**（把不可重试的当可重试），
 * 或者**明明只是网断了却只给一句提示、没有出路**（把可重试的当不可重试）。
 *
 * 两个方向的错都要有用例，所以下面既有 `retryable` 组也有 `rejected` 组，
 * 包括"HTTP 状态码"与"业务码"两种来源——它们共用 `NetworkException.code` 这一个字段。
 */
class ScanFailureMapperTest {

	private fun failureOf(error: Throwable): ScanResolveFailure =
		error.toScanResolveFailure()

	// ───────────── 可重试：网络类与 5xx ─────────────

	@Test
	fun `classifies network errors as retryable`() {
		listOf(
			NetworkException(NetworkException.CODE_NET_ERROR, "网络连接失败，请检查网络设置"),
			NetworkException(NetworkException.CODE_TIMEOUT, "请求超时，请检查网络后重试"),
			NetworkException(NetworkException.CODE_SERVER_ERROR, "服务器繁忙，请稍后重试")
		).forEach { error ->
			assertTrue(
				"网络类失败必须可重试：code=${error.code}",
				failureOf(error) is ScanResolveFailure.Network
			)
		}
	}

	@Test
	fun `classifies http 5xx as retryable`() {
		// httpFailure 把非 2xx 的**状态码**直接放进 NetworkException.code，
		// 所以 500/503 走的是区间判断而不是常量比较——两条路都要覆盖
		listOf(500, 502, 503, 504).forEach { status ->
			assertTrue(
				"HTTP $status 是服务端临时故障，值得重试",
				failureOf(NetworkException(status, "服务器繁忙，请稍后重试")) is ScanResolveFailure.Network
			)
		}
	}

	@Test
	fun `classifies unknown throwable as retryable with fallback message`() {
		// 认不出的异常：宁可让用户重试，也不要给一句"未知错误"且无路可走
		val failure = failureOf(IllegalStateException("boom"))
		assertTrue("认不出的异常按可重试处理", failure is ScanResolveFailure.Network)
		assertEquals("message 要透传", "boom", failure.message)
	}

	@Test
	fun `uses fallback message when message is blank or null`() {
		listOf(IllegalStateException(""), IllegalStateException()).forEach { error ->
			assertEquals(
				"文案为空时必须兜底，否则界面上会出现一句空提示",
				"校验失败，请重试", failureOf(error).message
			)
		}
	}

	// ───────────── 不可重试：客户端入参 / 权限 / 不存在 / 业务码 ─────────────

	@Test
	fun `classifies not a bluebook code as rejected`() {
		// 15001 来自 2xx 响应体里的业务码（ApiCall 的 apiCall 分支）
		val failure = failureOf(NetworkException(15001, "这不是小蓝书的二维码"))
		assertTrue("码本身有问题，重试必然还是同样结果", failure is ScanResolveFailure.Rejected)
		assertEquals("服务端的中文文案要原样透出", "这不是小蓝书的二维码", failure.message)
	}

	@Test
	fun `classifies permission and not found as rejected`() {
		listOf(
			403 to "该内容暂时无法查看",
			404 to "视频不存在或已被删除",
			400 to "请求参数有误"
		).forEach { (status, message) ->
			assertTrue(
				"HTTP $status 重试无意义",
				failureOf(NetworkException(status, message)) is ScanResolveFailure.Rejected
			)
		}
	}

	@Test
	fun `classifies parse error as rejected`() {
		// 响应体解析不了、响应体为空：重试不会让服务端换个响应回来
		assertTrue(
			"解析错误不该给重试",
			failureOf(NetworkException(NetworkException.CODE_PARSE_ERROR, "数据解析错误"))
				is ScanResolveFailure.Rejected
		)
	}

	@Test
	fun `classifies auth invalid as rejected not retryable loop`() {
		// 401 若判成可重试，配合 TokenAuthenticator 会变成"刷 token 失败 → 重试 → 再 401"，
		// 用户看到的是一个永远转圈的按钮
		assertTrue(
			"登录态失效不该给「重试」",
			failureOf(NetworkException(NetworkException.CODE_AUTH_INVALID, "登录已过期，请重新登录"))
				is ScanResolveFailure.Rejected
		)
	}
}
