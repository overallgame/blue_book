package com.example.blue_book.ui.scan

import com.example.blue_book.scan.ScanTarget

/**
 * 提示卡片上可点的动作。**与平台无关**——真要点设置、开浏览器由 Activity 翻译成系统调用，
 * 这里只表达"该给用户哪些出路"。
 */
enum class NoticeAction {

	/** 用 `pendingPayload` 重新校验 */
	RETRY,

	/** 清掉提示，回到干净扫描态 */
	CONTINUE,

	/** 打开相册选图 */
	PICK_IMAGE,

	/** 再次申请相机权限（系统还能弹框时） */
	REQUEST_PERMISSION,

	/** 跳系统设置页（系统不再弹框时） */
	OPEN_SETTINGS,

	/** 复制 [ScanNotice.content] */
	COPY,

	/** 用浏览器打开 [ScanNotice.content]（只可能是解析器认过的 http(s)） */
	OPEN_BROWSER
}

/**
 * 提示卡片的**展示模型**：标题 +（可选）原文 + 最多两个动作。
 *
 * 失败矩阵（设计方案 7.2 的 11 行）在这一层落地：这里的每一行都是一条可断言的用例
 * （`ScanNoticeTest`）。分工是 [ScanUiState.toNotice] 决定**给什么**（纯函数、可穷举测试），
 * Activity 只负责把 [NoticeAction] 翻译成系统调用（不可测，也不需要测）。
 *
 * 卡片一次只表达一件事：错误优先于扫到的内容——网络失败时 `detected` 仍有值
 * （它是刚才那个站内码），而此刻用户要看到的是失败原因与重试，不是那个引用。
 */
data class ScanNotice(
	val title: String,
	/** 原文。**同时是「复制」的内容**，所以登录票据刻意不给（见下） */
	val content: String? = null,
	/** 主按钮；两个都为 null 表示这是一条纯提示（例如"正在识别…"） */
	val primary: NoticeAction? = null,
	/** 次按钮，可无 */
	val secondary: NoticeAction? = null
)

/**
 * 状态 → 卡片。返回 null 表示"不显示卡片"（正在准备相机、正常扫描中）。
 *
 * 这里是**失败矩阵里"要说话"的那些行**（7.2 的 13 行中会产生卡片的部分）：
 *
 * | 场景 | 卡片与出路 |
 * |---|---|
 * | 权限被拒（还能问） | 「再次申请」+「从相册选图」 |
 * | 权限被拒（不能再问） | 「去设置」+「从相册选图」 |
 * | 设备无相机 / 相机被占用 | 只有「从相册选图」——申请权限或去设置都是徒劳 |
 * | 外部链接 | 展示原文 + 「复制」+「用浏览器打开」（**不自动打开**） |
 * | 纯文本 | 展示原文 + 「复制」 |
 * | 登录票据 | 只提示"即将上线"，**不展示内容也不给复制** |
 * | 校验网络失败 | 具体原因 + 「重试」+「继续扫」，`pendingPayload` 保留（不必重扫） |
 * | 码失效/已删/无权 | 服务端中文 + 「继续扫」 |
 * | 相册图里没有码 / 读不出来 | 「重新选图」+「继续扫」 |
 * | 正在校验 | 只提示"正在识别…"，**刻意不给按钮** |
 *
 * 矩阵里剩下的几行**故意不产生卡片**，所以它们的"实现"就是下面某个 `null`：
 * 画面有码但识别不出（继续扫，只靠取景引导）、同一码连续触发（去重、用户无感）、
 * 切后台恢复（生命周期）、相册选图取消（用户主动取消不是错误，不打扰）、
 * 跳转后目标页自己再失败（由目标页显示，本页已 finish）。
 */
fun ScanUiState.toNotice(): ScanNotice? {
	// 错误优先：有错误时它才是用户此刻要知道的事
	// （网络失败时 `detected` 仍有值——那是刚才那个站内码，不是此刻该展示的东西）
	error?.let { return it.toNotice() }

	return when (val current = phase) {
		ScanPhase.CheckingPermission -> null

		// 纯提示，刻意不给按钮：`UdfViewModel` 的 intent 由单一 collector 串行消费，
		// 校验期间发出的"取消"要等校验跑完才会被处理——给一个按了不生效的按钮比不给更糟
		ScanPhase.Resolving -> ScanNotice("正在识别…")

		is ScanPhase.PermissionDenied -> ScanNotice(
			title = "无法使用相机",
			content = if (current.canAskAgain) {
				"扫码需要相机权限，也可以从相册选择图片识别"
			} else {
				"相机权限已被拒绝，请到系统设置里开启，或从相册选择图片识别"
			},
			primary = if (current.canAskAgain) NoticeAction.REQUEST_PERMISSION else NoticeAction.OPEN_SETTINGS,
			secondary = NoticeAction.PICK_IMAGE
		)

		// 刻意不给权限相关按钮：没有相机时申请权限、翻设置都解决不了问题
		ScanPhase.CameraUnavailable -> ScanNotice(
			title = "相机不可用",
			content = "未找到可用的相机，或被其它应用占用。可以从相册选择图片识别",
			primary = NoticeAction.PICK_IMAGE
		)

		// 扫到的内容不是站内码：按 R4 展示原文并让用户自己决定，**不自动跳转/打开**
		ScanPhase.Scanning -> detected?.toNotice()
	}
}

/**
 * 非站内码的展示。
 *
 * **登录票据刻意不展示内容、也不给「复制」**：它是能换登录态的一次性凭据，
 * 设计方案 4.1 约定它"不缓存、不打日志"——把它显示在屏幕上、或放进系统剪贴板
 * （任何 App 都能读）与"不打日志"是同一个问题的不同形式。Phase 2 落地时这里换成真正的登录流程。
 */
private fun ScanTarget.toNotice(): ScanNotice? = when (this) {
	// 站内码不展示：它就是"指向本站"，用户要看的是校验后的结果（成功则直接跳走，失败则走错误卡片）
	is ScanTarget.InternalCode -> null

	is ScanTarget.ExternalUrl -> ScanNotice(
		title = "扫到外部链接，未自动打开",
		content = url,
		primary = NoticeAction.COPY,
		secondary = NoticeAction.OPEN_BROWSER
	)

	is ScanTarget.PlainText -> ScanNotice(
		title = "扫到文本",
		content = text,
		primary = NoticeAction.COPY
	)

	is ScanTarget.LoginTicket -> ScanNotice(
		title = "扫码登录即将上线",
		content = null,
		primary = NoticeAction.CONTINUE
	)
}

private fun ScanError.toNotice(): ScanNotice = when (this) {
	// 保留已扫到的内容 → 用户按「重试」不必重扫（N8）。
	// 标题用具体原因（超时 / 连不上 / 服务端繁忙）而不是笼统的"网络失败"：
	// 排查方向完全不同，而这两种原因客户端本来就分得开
	is ScanError.Network -> ScanNotice(
		title = reason,
		primary = NoticeAction.RETRY,
		secondary = NoticeAction.CONTINUE
	)

	// 文案直接用服务端的中文：它比客户端更清楚原因（"视频不存在或已被删除"）
	is ScanError.Rejected -> ScanNotice(
		title = message,
		primary = NoticeAction.CONTINUE
	)

	// "图里没有码"与"图读不出来"对用户的**出路相同**（重新选一张），只是原因不同——
	// 所以文案要跟着原因说实话，而不是一律说"没有识别到二维码"
	is ScanError.NoCodeInImage -> ScanNotice(
		title = if (unreadable) "无法读取这张图片，请换一张" else "这张图里没有识别到二维码",
		primary = NoticeAction.PICK_IMAGE,
		secondary = NoticeAction.CONTINUE
	)
}
