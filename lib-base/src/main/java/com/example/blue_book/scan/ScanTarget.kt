package com.example.blue_book.scan

/**
 * 扫码得到的内容归类。四类互斥，**"哪一类"决定了后续完全不同的流程**：
 *
 * - [InternalCode] → 交后端 `/scan/resolve` 校验后才决定跳转
 * - [LoginTicket]  → 不走 resolve，本地二次确认后交后端绑定
 * - [ExternalUrl]  → 不自动打开，只展示 + 复制（http(s) 额外提供"用浏览器打开"）
 * - [PlainText]    → 展示 + 复制。**注意它不是"错误"**：用户可能就想扫个纯文本码
 *
 * 用 sealed 而不是 `type: String` + 若干 nullable 字段，是为了让使用方的 `when` 能穷举：
 * 将来新增一类码时，编译器会逼着每个使用点处理，而不是静默漏掉。
 */
sealed interface ScanTarget {

	/**
	 * 站内码。**携带原始字符串**（已 trim）而不是解析后的 id —— 交后端裁定，
	 * 这样后端格式变更时老版本客户端也能正确工作（判据不在客户端）。
	 */
	data class InternalCode(val raw: String) : ScanTarget

	/** 登录票据（Phase 2）。只取票据本身，客户端不解析其内容、不缓存、不打日志。 */
	data class LoginTicket(val ticket: String) : ScanTarget

	/** 外部 http(s) 链接。不自动打开。 */
	data class ExternalUrl(val url: String) : ScanTarget

	/** 其它文本（含空白、超长、非 http(s) scheme 的 URL）。原样保留以便展示与复制。 */
	data class PlainText(val text: String) : ScanTarget
}
