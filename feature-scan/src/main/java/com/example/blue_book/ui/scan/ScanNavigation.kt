package com.example.blue_book.ui.scan

import android.content.Context
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.domain.model.ContentKind
import com.example.blue_book.domain.model.ScannedContent
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.router.openVideoPlayer
import com.therouter.TheRouter

/**
 * 校验成功后的跳转（设计方案 7.3）。
 *
 * **不新增页面**：视频走既有的 `openVideoPlayer`，用户走既有的作者主页路由——
 * 两者都是已存在的契约，扫码只是又一个入口。
 */
fun openScannedContent(context: Context, content: ScannedContent) {
	when (content.kind) {
		// 五个 extra 统一在 openVideoPlayer 里拼（那是唯一的入口函数，调用点不要各自拼）
		ContentKind.VIDEO -> openVideoPlayer(context, content.toVideoCardInfo())

		ContentKind.USER -> TheRouter.build(RoutePath.USER_PROFILE)
			.withLong(ExtraKeys.EXTRA_USER_ID, content.targetId)
			.navigation(context)
	}
}

/**
 * 扫码结果 → 播放页要的卡片。
 *
 * **纯函数，所以能测**：这是"哪个字段进哪个槽"的知识，写错了不崩、只是页面内容错位，
 * 靠真机肉眼很难发现。
 *
 * 只填得出来的字段。空缺的那几个是**有意留空**，与后端契约一致（设计方案 6.2）：
 * - `playUrl` 空 → 播放页**已有的兜底**会自己去取地址（`VideoAdapter` 见 playUrl 为空
 *   即触发 `onRequestPlayUrl`），所以不影响播放
 * - `avatar` / `uploaderId` 不在 resolve 契约里 → 头像显示占位图、点头像不跳作者页
 * - 计数（赞/藏/评论）留 0：这次扫描没有取，也不该拿"0"当真实数据展示给用户之前
 *   先跳过去——目标页自己会拉真实数据
 */
internal fun ScannedContent.toVideoCardInfo(): VideoCardInfo = VideoCardInfo(
	aid = targetId,
	// cid 与 aid 不同源：后端 getPlayUrl 忽略 cid（只按 aid 取 hls 地址），
	// 与 VideoMappers 一致地传 0，不要自造一个假的关联 id
	cid = 0,
	like = 0,
	image = cover.orEmpty(),
	avatar = "",
	collection = 0,
	nickname = subtitle.orEmpty(),
	description = title,
	playUrl = "",
	isLike = false,
	isCollect = false
)
