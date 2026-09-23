package com.example.blue_book.scan

/**
 * 分享文案：名字一行、站内码链接一行。
 *
 * 放在 lib-base 是因为两个模块都用它（`feature-video` 分享视频、`feature-mine` 分享主页），
 * 各自拼一遍会漂移成"同一个码两种说法"。链接由 [ScanCodeFormat] 生成，
 * 链接前那句提示见 [OPEN_HINT]。
 */
object ShareCode {

	/** 当前码域名在浏览器里打不开，所以必须告诉对方"用扫一扫"；有真域名后改这一行 */
	private const val OPEN_HINT = "用「小蓝书」扫一扫打开："

	/**
	 * 分享视频。
	 *
	 * @param title 视频标题（来自服务端）。为空时退化成一个通用说法——
	 *   宁可少一句描述，也不要给出一段"分享视频：《》"这种看起来像坏了的话。
	 * @param aid 视频 id，**必须为正数**：`ScanCodeFormat.videoUrl` 会 `require` 它，
	 *   因为 id ≤ 0 说明上游数据错了（那是编程错误，应当响亮失败而不是发一个扫不出来的码）
	 */
	fun video(title: String, aid: Long): String =
		text(name = title.trim().ifBlank { "小蓝书视频" }, url = ScanCodeFormat.videoUrl(aid))

	/** 分享某人的主页。@param nickname 为空时同样退化，理由同上 */
	fun user(nickname: String, id: Long): String =
		text(name = "${nickname.trim().ifBlank { "小蓝书用户" }}的主页", url = ScanCodeFormat.userUrl(id))

	/**
	 * 名字一行、链接一行。
	 *
	 * 分两行不是为了好看：聊天应用会把单独成行的 URL 识别成链接（有真域名后可点），
	 * 混在一句话中间则常常只被当成普通文字。
	 */
	private fun text(name: String, url: String): String = "$name\n$OPEN_HINT$url"
}
