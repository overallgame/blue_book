package com.example.blue_book.domain.repository

/**
 * 解析失败的**领域分类**。
 *
 * ## 为什么不让 ViewModel 直接看 `NetworkException`
 *
 * 出路不同（设计方案 7.2）：
 * - [Network] → 给"重试"，用户重试是有意义的
 * - [Rejected] → 只把服务端的中文文案原样显示，**不给重试**（码已失效/无权/内容已删，重试必然还是失败）
 *
 * 这个区分只有数据层知道（它拿得到 HTTP 状态码与业务码）。分类**在数据层做完**，
 * ViewModel 才不用 import `com.example.blue_book.network.*`——否则"哪一层知道 HTTP 状态码"
 * 这条边界会在第一次需要判断 403 与超时的区别时就被打破，之后再想收回来就贵了。
 *
 * 名字带 Failure 而不是 Error：它是 `Result` 的失败载体（必须是 Throwable），
 * 与 `ScanError`（UI 状态里的 sealed）不是一回事——后者是"给用户看的原因"，
 * 前者是"仓库怎么失败的"。两者之间的翻译在 ViewModel 里做一次。
 */
sealed class ScanResolveFailure(message: String) : Exception(message) {

	/** 出路：重试。网络不通、超时、服务端 5xx/繁忙——这次失败不代表下次也失败。 */
	class Network(message: String) : ScanResolveFailure(message)

	/**
	 * 出路：仅提示。
	 * message 直接用服务端返回的中文（"这不是小蓝书的二维码"/"视频不存在或已被删除"/
	 * "该内容暂时无法查看"），这是项目既有做法——服务端比客户端更清楚原因。
	 */
	class Rejected(message: String) : ScanResolveFailure(message)
}
