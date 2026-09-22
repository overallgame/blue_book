package com.example.bluebook.scan.dto

/** 扫码解析出的目标类型。序列化成 `"VIDEO"` / `"USER"`，客户端按名字映射成枚举。 */
enum class ScanTargetType { VIDEO, USER }

/**
 * `GET /api/v2/scan/resolve` 的响应体（设计方案 6.2 的契约）。
 *
 * 只有**"是哪一类 + 指向谁 + 展示三件套"**，刻意不是一份完整的 Video2Dto：
 * - 展示信息是扫码结果页/跳转落点要用的，少了它客户端要么显示空白、要么多打一次请求
 * - 而播放地址、点赞收藏状态、头像等**属于目标页自己的加载逻辑**，放进来会让这个接口
 *   一步步长成"另一个视频详情接口"，两处字段从此各自演化
 *
 * `title` 不可空（服务端保证有值）；`subtitle`/`cover` 可空（作者可能没昵称或没头像）。
 */
data class ScanResolveDto(
	val type: ScanTargetType,
	val targetId: Long,
	val title: String,
	val subtitle: String? = null,
	val cover: String? = null
)
