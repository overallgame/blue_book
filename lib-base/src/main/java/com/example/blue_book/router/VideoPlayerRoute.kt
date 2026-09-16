package com.example.blue_book.router

import android.content.Context
import com.example.blue_book.data.VideoCardInfo
import com.therouter.TheRouter

/**
 * 打开播放页（独立页面，压在当前页面之上）。
 *
 * 所有「从非 Tab 页面点视频」的入口都走这里，调用点不要各自拼 extra：
 * 播放页需要 4 个参数（视频本体 + 来源标记 + 关键词 + 来源用户 id），散在 8 个调用点
 * 容易漏，而漏掉来源标记的表现是「续拉变成了推荐流」——不崩溃，只是行为微妙地不对。
 *
 * 底部导航的「视频」Tab 不走这里：那是沉浸式播放 Feed，是另一个入口。
 *
 * @param source 列表来源标记：search / liked / collected / user_videos；为空则不做同源续拉
 * @param keyword 搜索关键词（仅 source = search）
 * @param userId 作品列表所属用户 id（仅 source = user_videos）
 */
fun openVideoPlayer(
	context: Context,
	item: VideoCardInfo,
	source: String? = null,
	keyword: String? = null,
	userId: Long = 0L
) {
	TheRouter.build(RoutePath.VIDEO_PLAYER).apply {
		withParcelable(ExtraKeys.EXTRA_VIDEO, item)
		if (source != null) withString(ExtraKeys.EXTRA_SOURCE, source)
		if (keyword != null) withString(ExtraKeys.EXTRA_KEYWORD, keyword)
		if (userId != 0L) withLong(ExtraKeys.EXTRA_SOURCE_USER_ID, userId)
	}.navigation(context)
}
