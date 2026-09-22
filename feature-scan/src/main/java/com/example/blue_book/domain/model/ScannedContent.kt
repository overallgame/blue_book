package com.example.blue_book.domain.model

/**
 * 扫码解析出的目标类型。
 *
 * ★ 为什么是枚举而不是 `String`：服务端的 `type` 字段是字符串，**在数据层边界转换一次**，
 * 之后全用枚举——否则"VIDEO"这个魔法字符串会散落到跳转、埋点、日志各处，
 * 打错一个字母不会编译报错，只会静默走到 else 分支。
 *
 * 枚举值刻意与后端 `ScanTargetType`（`backend/.../scan/dto/ScanResolveDto.kt`）**同名**，
 * 靠名字对应而不是序号：后端调换枚举顺序时不会让老客户端把视频当成用户。
 */
enum class ContentKind {
	VIDEO,
	USER;

	companion object {

		/**
		 * 服务端下发的是 `"VIDEO"` / `"USER"`。大小写不敏感以便后端改风格。
		 *
		 * **不认识就返回 null，不猜**：后端将来加新类型（比如话题页）时，
		 * 老客户端必须能明确说出"暂不支持"，而不是把用户导航到一个错误的地方。
		 */
		fun fromWire(raw: String?): ContentKind? {
			val value = raw?.trim() ?: return null
			return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
		}
	}
}

/**
 * 一次成功解析的结果（domain 模型）。
 *
 * 字段与后端 `ScanResolveDto` 有意不同：那边全可空（服务端可能给 null），
 * 这边 **kind / targetId / title 不可空**——把"字段是否齐全"的判断收在
 * `ScanMappers` 一处（映射不过去就失败），之后所有消费方都不必再判空。
 *
 * `title` 允许为空串：标题为空是"展示得不好看"，不该让用户扫不出这条码。
 */
data class ScannedContent(
	val kind: ContentKind,
	val targetId: Long,
	val title: String,
	val subtitle: String? = null,
	/** 已拼成绝对地址（服务端下发相对路径，见 [com.example.blue_book.util.absoluteUrl]） */
	val cover: String? = null
)
