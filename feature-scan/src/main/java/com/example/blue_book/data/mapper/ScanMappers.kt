package com.example.blue_book.data.mapper

import com.example.blue_book.data.remote.dto.ScanResolveDto
import com.example.blue_book.domain.model.ContentKind
import com.example.blue_book.domain.model.ScannedContent
import com.example.blue_book.util.absoluteUrl

/**
 * DTO → domain。**映射是唯一"把门"的地方**：过了这里，下游字段就都是非空的。
 *
 * 返回 null 表示"这份响应映射不出一个能跳转的目标"（类型不认识 / 缺 targetId）。
 * 由仓库翻成一句明确的中文提示，**不静默降级**——比如"类型不认识就当成纯文本展示"
 * 会让用户以为码没问题，而实际上是我们版本落后。
 *
 * 标题为空**不算映射失败**（与后端一致：没有标题时后端已退回描述）：
 * 标题难看一点不该让用户扫不出这条码。
 */
internal fun ScanResolveDto.toScannedContent(baseUrl: String): ScannedContent? {
	val kind = ContentKind.fromWire(type) ?: return null
	val id = targetId?.takeIf { it > 0 } ?: return null
	return ScannedContent(
		kind = kind,
		targetId = id,
		title = title?.trim().orEmpty(),
		subtitle = subtitle?.trim()?.ifBlank { null },
		// 服务端下发相对路径（与 /videos/*/dto 一致），host 由客户端补
		cover = absoluteUrl(baseUrl, cover)
	)
}
