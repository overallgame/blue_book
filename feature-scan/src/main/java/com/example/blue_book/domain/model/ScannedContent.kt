package com.example.blue_book.domain.model

/**
 * 扫码解析出的目标类型。
 *
 * 枚举值刻意与后端 `ScanTargetType`（`backend/.../scan/dto/ScanResolveDto.kt`）同名：
 * 靠名字对应而不是序号，后端调换枚举顺序不会让老客户端把视频当成用户。
 */
enum class ContentKind {
	VIDEO,
	USER;

	companion object {

		/**
		 * 服务端下发的是 `"VIDEO"` / `"USER"`，大小写不敏感。
		 *
		 * **不认识就返回 null，不猜**：后端加新类型时老客户端要能明确说"暂不支持"，
		 * 而不是把用户导航到错误的地方。
		 */
		fun fromWire(raw: String?): ContentKind? {
			val value = raw?.trim() ?: return null
			return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
		}
	}
}

/**
 * 一次成功解析的结果。
 *
 * 字段不可空（映射时把关，见 `ScanMappers`）；`title` 允许空串——标题难看不该让码扫不出来。
 */
data class ScannedContent(
	val kind: ContentKind,
	val targetId: Long,
	val title: String,
	val subtitle: String? = null,
	/** 已拼成绝对地址（服务端下发相对路径，见 [com.example.blue_book.util.absoluteUrl]） */
	val cover: String? = null
)
