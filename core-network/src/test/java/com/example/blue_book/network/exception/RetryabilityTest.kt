package com.example.blue_book.network.exception

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "该不该重试"的判据穷举。
 *
 * 两个方向的错都要有用例，因为它们对用户的伤害不同：
 * - 把**不可重试**的判成可重试 → 用户按了重试，转三秒，还是同样的错误
 * - 把**可重试**的判成不可重试 → 明明只是网抖了一下，页面却只给一句提示、没有出路
 *
 * 这份判据现有两个消费方（扫码校验的失败分类、分片上传的分片重试），
 * 所以它放在 core-network 而不是各模块自己实现——这正是本文件要守的东西。
 *
 * 注意 JUnit 4 的签名是 `assertEquals(message, expected, actual)`，消息在**第一个**。
 */
class RetryabilityTest {

	// ───────────── 值得重试：网络类与 5xx ─────────────

	@Test
	fun `network failures are retryable`() {
		listOf(
			NetworkException(NetworkException.CODE_NET_ERROR, "网络连接失败，请检查网络设置"),
			NetworkException(NetworkException.CODE_TIMEOUT, "请求超时，请检查网络后重试"),
			NetworkException(NetworkException.CODE_SERVER_ERROR, "服务器繁忙，请稍后重试")
		).forEach { error ->
			assertTrue("网络类失败必须可重试：code=${error.code}", error.isRetryableNetworkFailure())
		}
	}

	@Test
	fun `http 5xx is retryable`() {
		// httpFailure 把非 2xx 的**状态码**直接放进 NetworkException.code，
		// 所以 5xx 走的是区间判断而不是常量比较——两条路都要覆盖
		listOf(500, 502, 503, 504).forEach { status ->
			assertTrue(
				"HTTP $status 是服务端临时故障，值得重试",
				NetworkException(status, "服务器繁忙").isRetryableNetworkFailure()
			)
		}
	}

	@Test
	fun `unknown throwable is retryable`() {
		// 认不出的异常：宁可让用户重试，也不要给一句"未知错误"且无路可走
		assertTrue(IllegalStateException("boom").isRetryableNetworkFailure())
		assertTrue(RuntimeException().isRetryableNetworkFailure())
	}

	// ───────────── 不值得重试：入参 / 权限 / 不存在 / 业务码 ─────────────

	@Test
	fun `http 4xx is not retryable`() {
		listOf(400, 403, 404, 410).forEach { status ->
			assertFalse(
				"HTTP $status 重试无意义",
				NetworkException(status, "请求失败").isRetryableNetworkFailure()
			)
		}
	}

	@Test
	fun `business codes are not retryable`() {
		// 业务码来自 2xx 响应体（code != 0）：码本身或请求本身有问题，重试必然一样
		listOf(
			15001 to "这不是小蓝书的二维码",
			13002 to "分片缺失，请重新上传缺失的分片",
			13005 to "分片序号越界"
		).forEach { (code, message) ->
			assertFalse(
				"业务码 $code（$message）不该给重试",
				NetworkException(code, message).isRetryableNetworkFailure()
			)
		}
	}

	@Test
	fun `auth invalid is not retryable`() {
		// 401 若判成可重试，配合 TokenAuthenticator 会变成
		// "刷 token 失败 → 重试 → 再 401"，用户看到的是一个永远转圈的按钮
		assertFalse(
			NetworkException(NetworkException.CODE_AUTH_INVALID, "登录已过期，请重新登录")
				.isRetryableNetworkFailure()
		)
	}

	@Test
	fun `parse error is not retryable`() {
		// 响应体解析不了、响应体为空：重试不会让服务端换个响应回来
		assertFalse(
			NetworkException(NetworkException.CODE_PARSE_ERROR, "数据解析错误").isRetryableNetworkFailure()
		)
	}
}
