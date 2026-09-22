package com.example.bluebook.scan

/**
 * 站内码格式的**服务端实现**：回答"这段字符串算不算小蓝书的码、指向谁"。
 *
 * ## 为什么服务端要有一份（而不是信客户端）
 *
 * 客户端 `ScanCodeFormat.parse()`（`lib-base/scan/ScanCodeFormat.kt`）已经判过一次了，
 * 但那次判定的结论**不能作为服务端的输入**：客户端只传原始字符串（`payload`），
 * 由服务端重新判。否则就等于把判据放在客户端——后端改码格式时，
 * 落后的 App 版本（改不动）会给出错误结论，而它自己无从发现。
 *
 * ## ★ 与客户端是**同一份契约的两份实现**，改动必须成对
 *
 * Kotlin 无法跨工程共享代码（Android Gradle 工程与 `:backend` 是两个独立编译单元，
 * 没有共同源码集）。所以这是一处**刻意的重复**，代价与防错手段都写在这里：
 *
 * | 常量 | 客户端 | 服务端 |
 * |---|---|---|
 * | `CODE_HOST` | `lib-base/.../scan/ScanCodeFormat.kt` | 本文件 |
 * | 路径段 `v` / `u` | 同上（`videoUrl()` / `userUrl()`） | 同上 |
 *
 * 漂移的后果不是"颜色不对"而是**自己分享出去的码自己扫不出来**，所以配了自动检查：
 * `python tools/check_scan_code_contract.py`（在 CI/本地都能跑，两处不一致时失败）。
 *
 * ## 与客户端行为上**唯一的有意差异**
 *
 * 客户端多认一类 `/qr/{ticket}`（登录码，Phase 2，不走本接口，见设计方案 8.2），
 * 因此这里不认它——落到这里只可能是"客户端和服务端版本不一致"，
 * 报「这不是小蓝书的二维码」比假装能处理更诚实。
 */
object ScanCodeFormat {

	/**
	 * 码里的 host。本期是 RFC 2606 保留域（保证永不解析）。
	 * 将来有真域名：改这里 **并把新域名追加进** [ACCEPTED_HOSTS]，**老的码继续有效**。
	 */
	const val CODE_HOST: String = "bluebook.invalid"

	/** 只加不删：删掉等于让已经发出去的码失效。 */
	private val ACCEPTED_HOSTS: List<String> = listOf(CODE_HOST)

	private const val SCHEME = "https"

	private const val PATH_VIDEO = "v"
	private const val PATH_USER = "u"

	/** 与客户端一致的超长上限：二维码能塞几 KB，而我们要的格式都很短。 */
	private const val MAX_PAYLOAD_LENGTH = 2048

	private val HTTP_URL = Regex(
		"^(https?)://([^/?#:]+)(?::(\\d+))?([^?#]*)(?:\\?[^#]*)?(?:#.*)?$",
		RegexOption.IGNORE_CASE
	)

	/** 与客户端 `ScanCodeFormat.kt` 的 `VIDEO_PATH`/`USER_PATH` 一一对应。 */
	private val VIDEO_PATH = Regex("^/$PATH_VIDEO/(\\d+)/?\$")
	private val USER_PATH = Regex("^/$PATH_USER/(\\d+)/?\$")

	/**
	 * 归类。**不查库**——只回答"是不是我们的码、指向什么"。
	 *
	 * 返回 null 表示"不是小蓝书的二维码"（含畸形、超长、外域、非法 id）。
	 * 这里刻意不抛异常：判定的唯一消费者是 `ScanService`，它要把 null 统一翻成
	 * [com.example.bluebook.common.NotBlueBookCodeException]。
	 *
	 * **不做子域/后缀匹配**：`bluebook.invalid.evil.com`、`a.bluebook.invalid` 都必须落空。
	 * 手写正则而不用 `java.net.URI`：这是安全边界，判定要完全可控、可穷举。
	 */
	fun parse(payload: String): ScanCode? {
		val text = payload.trim()
		if (text.isEmpty() || text.length > MAX_PAYLOAD_LENGTH) return null

		val match = HTTP_URL.matchEntire(text) ?: return null
		if (match.groupValues[2].lowercase() !in ACCEPTED_HOSTS) return null

		val path = match.groupValues[4]

		// toLongOrNull 而不是 toLong：`/v/` 后面可以是任意多位数字（payload 上限 2048 字符），
		// toLong 会抛 NumberFormatException → 被 handleUnknown 兜成 500「服务器繁忙」。
		// 畸形输入不该表现为服务端故障。`> 0` 同理挡掉 /v/0 —— 客户端生成侧 require(aid > 0)。
		val aid = VIDEO_PATH.matchEntire(path)?.groupValues?.get(1)?.toLongOrNull()
		if (aid != null && aid > 0) return ScanCode.Video(aid)

		val userId = USER_PATH.matchEntire(path)?.groupValues?.get(1)?.toLongOrNull()
		if (userId != null && userId > 0) return ScanCode.User(userId)

		return null
	}

	/**
	 * 生成侧。本期只有测试在用（R7 分享升级成链接时客户端也有同名方法），
	 * 放在这里是为了让"编"与"解"在同一个文件里对着看——格式契约的两半不能分居。
	 */
	fun videoUrl(aid: Long): String {
		require(aid > 0) { "视频 aid 必须为正数，实际为 $aid" }
		return "$SCHEME://$CODE_HOST/$PATH_VIDEO/$aid"
	}

	fun userUrl(id: Long): String {
		require(id > 0) { "用户 id 必须为正数，实际为 $id" }
		return "$SCHEME://$CODE_HOST/$PATH_USER/$id"
	}
}
