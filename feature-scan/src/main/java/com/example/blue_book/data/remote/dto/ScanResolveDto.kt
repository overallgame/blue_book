package com.example.blue_book.data.remote.dto

/**
 * `GET /api/v2/scan/resolve` 的响应形状（设计方案 5.4）。
 *
 * ★ **只活在 data 层**：ViewModel 拿到的是 `ScannedContent`，不是它。
 *
 * 字段全可空是**防御性的**，不是契约允许服务端下发 null：
 * 服务端保证 type/targetId/title 有值，但客户端不能把"对面一定守约"当作前提——
 * Gson 遇到缺失字段会给 null，而 JSON 里的 `null` 会绕过 Kotlin 的非空声明
 * （反序列化不走构造器的空检查），拿它当非空用就是 NPE。
 * 所以：DTO 全可空，由 `ScanMappers` 一处把关。
 */
data class ScanResolveDto(
	val type: String? = null,
	val targetId: Long? = null,
	val title: String? = null,
	val subtitle: String? = null,
	val cover: String? = null
)
