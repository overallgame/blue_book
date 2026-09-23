package com.example.blue_book.ui.scan

import com.example.blue_book.scan.ScanTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **失败矩阵的可执行版本**（设计方案 7.2 的 11 行）。
 *
 * 这一组用例的价值不在于"代码对不对"，而在于**把"失败时用户看得懂吗、有出路吗"钉住**。
 *
 * 断言分成两层：
 * 1. **该不该给卡片、给了哪些出路**（`primary`/`secondary`）——这是"有没有路可走"
 * 2. **原因文案是否说到了点子上**——含糊的文案等于没有出路（"网络失败"无法指导用户，
 *    "无法连接到服务器"可以）
 */
class ScanNoticeTest {

	private fun state(
		phase: ScanPhase = ScanPhase.Scanning,
		detected: ScanTarget? = null,
		error: ScanError? = null
	) = ScanUiState(phase = phase, detected = detected, error = error)

	// ───────────── 不显示卡片的两种情形 ─────────────

	@Test
	fun `no card while preparing camera`() {
		assertNull(
			"还在准备相机时不该弹卡片——那不是失败，只是还没准备好",
			state(phase = ScanPhase.CheckingPermission).toNotice()
		)
	}

	@Test
	fun `no card while scanning normally`() {
		assertNull(
			"正常扫描中不弹卡片，只靠取景引导",
			state(phase = ScanPhase.Scanning).toNotice()
		)
	}

	@Test
	fun `no card for internal code before resolve finishes`() {
		// 站内码自己不展示：用户要看的是校验后的结果（成功直接跳走，失败走错误卡片）
		assertNull(
			"站内码不该被当成「内容」展示出来",
			state(detected = ScanTarget.InternalCode("https://bluebook.invalid/v/1")).toNotice()
		)
	}

	// ───────────── 权限与相机（N1/R6）────────────

	@Test
	fun `permission denied with retry asks again and offers gallery`() {
		val notice = state(phase = ScanPhase.PermissionDenied(canAskAgain = true)).toNotice()

		assertEquals("还能再问时必须给「再次申请」", NoticeAction.REQUEST_PERMISSION, notice?.primary)
		assertEquals("相机一被拒，相册就成了必须的降级路径", NoticeAction.PICK_IMAGE, notice?.secondary)
	}

	@Test
	fun `permission denied permanently sends to settings`() {
		val notice = state(phase = ScanPhase.PermissionDenied(canAskAgain = false)).toNotice()

		assertEquals(
			"系统不再弹框时，唯一有效的是去设置里手动开",
			NoticeAction.OPEN_SETTINGS, notice?.primary
		)
		assertEquals("去设置之外仍要留相册这条路", NoticeAction.PICK_IMAGE, notice?.secondary)
	}

	@Test
	fun `camera unavailable offers gallery only`() {
		// "同款降级态、文案区分"的具体落地：没有相机时申请权限、翻设置都是徒劳，
		// 给了按钮就是给了个按了没用的东西（这正是把它独立成分支的理由）
		val notice = state(phase = ScanPhase.CameraUnavailable).toNotice()

		assertEquals("没有相机时唯一的出路是相册", NoticeAction.PICK_IMAGE, notice?.primary)
		assertNull("不该给权限相关按钮", notice?.secondary)
		assertTrue(
			"文案要说明是相机不可用，而不是「权限被拒」",
			notice!!.title.contains("相机")
		)
	}

	// ───────────── 非站内码：展示 + 复制（R4）────────────

	@Test
	fun `external url shows content with copy and browser actions`() {
		val notice = state(
			detected = ScanTarget.ExternalUrl("https://www.example.com/a?b=1")
		).toNotice()

		assertEquals("要展示原文，用户才知道扫到了什么", "https://www.example.com/a?b=1", notice?.content)
		assertEquals("默认动作是复制", NoticeAction.COPY, notice?.primary)
		assertEquals("http(s) 额外给「用浏览器打开」", NoticeAction.OPEN_BROWSER, notice?.secondary)
	}

	@Test
	fun `plain text offers copy only`() {
		val notice = state(detected = ScanTarget.PlainText("一段普通文本")).toNotice()

		assertEquals("纯文本同样要展示原文", "一段普通文本", notice?.content)
		assertEquals(NoticeAction.COPY, notice?.primary)
		assertNull("纯文本没有「用浏览器打开」", notice?.secondary)
	}

	@Test
	fun `login ticket does not expose its content`() {
		// 票据是能换登录态的一次性凭据：4.1 约定"不缓存、不打日志"，
		// 而"显示在屏幕上""放进系统剪贴板（任何 App 都能读）"是同一个问题的其他形式
		val notice = state(detected = ScanTarget.LoginTicket("ticket-abc-123")).toNotice()

		assertNull("票据内容不得出现在界面上（也就不会被复制）", notice?.content)
		assertEquals("只能「知道了」", NoticeAction.CONTINUE, notice?.primary)
		assertNull("不该有第二个动作", notice?.secondary)
	}

	// ───────────── 校验失败（R5/N8）────────────

	@Test
	fun `network failure offers retry and keeps reason specific`() {
		val notice = state(error = ScanError.Network("无法连接到服务器，请检查网络或服务是否可用")).toNotice()

		assertEquals("网络类失败必须给「重试」", NoticeAction.RETRY, notice?.primary)
		assertEquals("也给一条不重试的出路，别把用户困在重试里", NoticeAction.CONTINUE, notice?.secondary)
		assertEquals(
			"标题要用具体原因（超时与连不上的排查方向不同），不是笼统的「网络失败」",
			"无法连接到服务器，请检查网络或服务是否可用", notice?.title
		)
	}

	@Test
	fun `server rejection shows server message verbatim as the only hint`() {
		val notice = state(error = ScanError.Rejected("视频不存在或已被删除")).toNotice()

		assertEquals("服务端的中文文案原样展示（它比客户端更清楚原因）", "视频不存在或已被删除", notice?.title)
		assertEquals("仅提示，出路是回到扫描", NoticeAction.CONTINUE, notice?.primary)
		assertNull("不该给重试：重试还是同样的结果", notice?.secondary)
	}

	@Test
	fun `image without code offers pick again`() {
		val notice = state(error = ScanError.NoCodeInImage()).toNotice()

		assertEquals("相机解不出这张图，只能换一张", NoticeAction.PICK_IMAGE, notice?.primary)
		assertEquals(NoticeAction.CONTINUE, notice?.secondary)
		assertTrue("文案要说清是图的问题", notice!!.title.contains("这张图"))
	}

	@Test
	fun `unreadable image says so instead of blaming the code`() {
		// 用户看不出"文件损坏"与"图里没码"的区别，但文案说错会让他一直换图，
		// 而问题根本在文件本身
		val notice = state(error = ScanError.NoCodeInImage(unreadable = true)).toNotice()

		assertEquals(NoticeAction.PICK_IMAGE, notice?.primary)
		assertTrue("要说明是「读不出来」，不是「没有码」", notice!!.title.contains("读取"))
	}

	// ───────────── 优先级与进行中 ─────────────

	@Test
	fun `error takes precedence over a previously detected internal code`() {
		// 网络失败时 detected 仍是刚才那个站内码：此刻用户要看的是失败原因与重试，
		// 而不是那个内部引用
		val notice = state(
			detected = ScanTarget.InternalCode("https://bluebook.invalid/v/1"),
			error = ScanError.Network("请求超时，请检查网络后重试")
		).toNotice()

		assertEquals("错误优先", "请求超时，请检查网络后重试", notice?.title)
		assertNull("站内码本身没有可展示的内容", notice?.content)
	}

	@Test
	fun `resolving shows a plain notice without any button`() {
		// 这一条是"别给按了不生效的按钮"的体现：intent 由单一 collector 串行消费，
		// 校验期间发出的取消要等校验跑完才被处理
		val notice = state(phase = ScanPhase.Resolving).toNotice()

		assertTrue("要告诉用户正在识别", notice!!.title.contains("识别"))
		assertNull("没有可点的动作", notice.primary)
		assertNull(notice.secondary)
	}
}
