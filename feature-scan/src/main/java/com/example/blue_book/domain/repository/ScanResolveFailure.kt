package com.example.blue_book.domain.repository

/**
 * 解析失败的领域分类，两类对应两种出路：[Network] 给"重试"，[Rejected] 只用服务端文案提示。
 *
 * 分类在数据层完成（只有它拿得到 HTTP 状态码与业务码），ViewModel 因此不必知道网络类型。
 * 与 UI 层的 `ScanError` 是两回事：这里描述"仓库怎么失败的"，那里描述"给用户看的原因"。
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
