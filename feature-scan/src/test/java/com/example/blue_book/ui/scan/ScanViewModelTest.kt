package com.example.blue_book.ui.scan

import com.example.blue_book.scan.ScanCodeFormat
import com.example.blue_book.scan.ScanTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 状态机的单测（设计方案 7.1 的图 + 7.2 的表在**状态**这一侧的行为）。
 *
 * [ScanNoticeTest] 管的是"每种状态给什么出路"，这里管的是"**怎么走到那些状态**"：
 * 权限结论如何落成 phase、校验失败如何落成 error 并保住原文、去重与重试的边界。
 *
 * 全部跑在纯 JVM 上：不需要相机、不需要权限、不需要后端，靠 [FakeScanRepository]
 * 造出成功/网络失败/被拒三条路径。命名约定沿用项目既有写法：方法名英文、断言消息中文。
 */
class ScanViewModelTest {

	@get:Rule
	val mainDispatcherRule = MainDispatcherRule()

	/** 测试里用的站内码：直接调生产代码的生成函数，避免手写一个"看起来对"的字符串 */
	private val videoCode = ScanCodeFormat.videoUrl(42L)

	// ───────────── 权限与相机的落状态 ─────────────

	@Test
	fun `granted permission moves to scanning`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnCameraPermissionResult(granted = true, canAskAgain = true))

		assertEquals(ScanPhase.Scanning, vm.uiState.value.phase)
		assertTrue("相机在跑才分析", vm.uiState.value.analyzing)
	}

	@Test
	fun `denied permission keeps the canAskAgain flag from the platform`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnCameraPermissionResult(granted = false, canAskAgain = false))

		assertEquals(
			"能不能再问只有平台知道，状态机必须如实存下来给 UI 用",
			ScanPhase.PermissionDenied(canAskAgain = false), vm.uiState.value.phase
		)
		assertFalse("没有相机就不该分析", vm.uiState.value.analyzing)
	}

	@Test
	fun `camera unavailable is its own phase not a permission denial`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnCameraUnavailable)

		assertEquals(ScanPhase.CameraUnavailable, vm.uiState.value.phase)
	}

	@Test
	fun `permission result clears a previous error`() = runTest {
		// 授权成功是"换了个世界"，上一次的失败原因不该还压在上面
		val vm = ScanViewModel(FakeScanRepository())
		vm.dispatch(ScanIntent.OnImageWithoutCode())

		vm.dispatch(ScanIntent.OnCameraPermissionResult(granted = true, canAskAgain = true))

		assertNull("换阶段时错误要清掉", vm.uiState.value.error)
	}

	// ───────────── 站内码 → 校验 ─────────────

	@Test
	fun `sends raw payload of internal code to server`() = runTest {
		val repository = FakeScanRepository()
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		assertEquals(
			"传给服务端的必须是**原始字符串**，不是本地解析出来的 id（设计方案 6.2）",
			listOf(videoCode), repository.calls
		)
	}

	@Test
	fun `phase is resolving while the request is in flight`() = runTest {
		// 这是"Resolving 期间暂停分析器"的依据（N3/N4）：请求还没回来时必须是 Resolving，
		// 回来之后必须回到 Scanning（否则分析器被永久摘掉，相机再也扫不出东西）
		val gate = CompletableDeferred<Unit>()
		val repository = FakeScanRepository().apply {
			onResolve = { gate.await(); Result.success(FakeScanRepository.defaultContent()) }
		}
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))
		assertEquals("请求在飞的时候是 Resolving", ScanPhase.Resolving, vm.uiState.value.phase)
		assertFalse("Resolving 时不该分析", vm.uiState.value.analyzing)

		gate.complete(Unit)
		assertEquals("请求结束后回到扫描态", ScanPhase.Scanning, vm.uiState.value.phase)
		assertTrue("恢复分析", vm.uiState.value.analyzing)
	}

	@Test
	fun `analyzing is derived from phase not stored separately`() {
		// analyzing 刻意不是独立字段：存下来就允许"phase 是 Resolving 但 analyzing 还是 true"
		// 这种非法组合（正是设计方案 5.2 批评过的写法），而它又是 UI 要用的信息
		assertFalse(ScanUiState(phase = ScanPhase.CheckingPermission).analyzing)
		assertFalse(ScanUiState(phase = ScanPhase.PermissionDenied(canAskAgain = true)).analyzing)
		assertFalse(ScanUiState(phase = ScanPhase.CameraUnavailable).analyzing)
		assertFalse(ScanUiState(phase = ScanPhase.Resolving).analyzing)
		assertTrue(ScanUiState(phase = ScanPhase.Scanning).analyzing)
	}

	@Test
	fun `emits resolved with server content`() = runTest {
		val repository = FakeScanRepository().apply {
			onResolve = { Result.success(FakeScanRepository.defaultContent("扫到的视频")) }
		}
		val vm = ScanViewModel(repository)
		val effects = collectEffects(vm)

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		val resolved = effects.filterIsInstance<ScanEffect.Resolved>().singleOrNull()
		assertTrue("成功路径应当抛出 Resolved（跳转由 UI 做）", resolved != null)
		assertEquals("内容要原样带出来", "扫到的视频", resolved!!.content.title)
	}

	@Test
	fun `success clears pending payload`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		assertNull("成功了就没有「待处理的原文」了", vm.uiState.value.pendingPayload)
	}

	@Test
	fun `resolves only once while code is continuously visible`() = runTest {
		// 相机一秒回调几十次、同一个码会被反复解出：不挡住就是对同一个码连发请求
		val repository = FakeScanRepository()
		val vm = ScanViewModel(repository)

		repeat(20) { vm.dispatch(ScanIntent.OnCodeDetected(videoCode)) }

		assertEquals("同一个码在去重窗口内只应请求一次", 1, repository.calls.size)
	}

	// ───────────── 非站内码 → 不打扰服务端 ─────────────

	@Test
	fun `does not call server for external url`() = runTest {
		val repository = FakeScanRepository()
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected("https://www.example.com/article/1"))

		assertEquals("外部链接不该发校验请求", emptyList<String>(), repository.calls)
	}

	@Test
	fun `does not call server for plain text`() = runTest {
		val repository = FakeScanRepository()
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected("这就是一段普通文本"))

		assertEquals("纯文本不该发校验请求", emptyList<String>(), repository.calls)
	}

	@Test
	fun `does not call server for login ticket of phase two`() = runTest {
		// 登录码不走 resolve（设计方案 8.2），且 Phase 2 暂缓
		val repository = FakeScanRepository()
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected("https://${ScanCodeFormat.CODE_HOST}/qr/ticket123"))

		assertEquals("登录票据不该发校验请求", emptyList<String>(), repository.calls)
	}

	@Test
	fun `keeps detected target in state for the notice to render`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnCodeDetected("  https://www.example.com/x  "))

		val detected = vm.uiState.value.detected
		assertTrue("状态里应当留下识别结果，卡片才能据此展示", detected is ScanTarget.ExternalUrl)
		assertEquals(
			"存的是去掉首尾空白后的原文——展示与复制都要用它",
			"https://www.example.com/x", (detected as ScanTarget.ExternalUrl).url
		)
	}

	@Test
	fun `a new detection clears the previous error`() = runTest {
		// 上一次"码已失效"的卡片不能盖在这次的新结果上
		val repository = FakeScanRepository().apply {
			onResolve = { FakeScanRepository.rejectedFailure("视频不存在或已被删除") }
		}
		val vm = ScanViewModel(repository)
		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))
		assertTrue("先制造一个错误", vm.uiState.value.error != null)

		vm.dispatch(ScanIntent.OnCodeDetected("https://www.example.com/x"))

		assertNull("新的一次识别要清掉旧的错误", vm.uiState.value.error)
	}

	// ───────────── 失败 → 状态里的出路（N8/R5）─────────────

	@Test
	fun `network failure becomes retryable error and keeps the payload`() = runTest {
		val repository = FakeScanRepository().apply {
			onResolve = { FakeScanRepository.networkFailure() }
		}
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		val error = vm.uiState.value.error
		assertTrue("网络类失败要落成可重试的 error", error is ScanError.Network)
		assertEquals(
			"具体原因要带出来（超时与连不上排查方向不同）",
			"网络连接失败，请检查网络设置", (error as ScanError.Network).reason
		)
		assertEquals("原文必须保留：用户重试不该重新对准二维码", videoCode, vm.uiState.value.pendingPayload)
		assertEquals("失败后回到扫描态（相机继续工作）", ScanPhase.Scanning, vm.uiState.value.phase)
	}

	@Test
	fun `rejection becomes non retryable error with server message`() = runTest {
		val repository = FakeScanRepository().apply {
			onResolve = { FakeScanRepository.rejectedFailure("视频不存在或已被删除") }
		}
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		val error = vm.uiState.value.error
		assertTrue("服务端拒绝要落成 Rejected（出路不同）", error is ScanError.Rejected)
		assertEquals("服务端的中文原文要透出", "视频不存在或已被删除", (error as ScanError.Rejected).message)
	}

	@Test
	fun `unknown throwable becomes retryable network error with fallback text`() = runTest {
		val repository = FakeScanRepository().apply {
			onResolve = { Result.failure(IllegalStateException("")) }
		}
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		val error = vm.uiState.value.error
		assertTrue("认不出的异常按可重试处理", error is ScanError.Network)
		assertEquals("没有文案时要有兜底", "网络连接失败，请重试", (error as ScanError.Network).reason)
	}

	// ───────────── 重试（N8 的核心：不必重扫）─────────────

	@Test
	fun `retry resends the kept payload without rescanning`() = runTest {
		val repository = FakeScanRepository().apply {
			onResolve = { FakeScanRepository.networkFailure() }
		}
		val vm = ScanViewModel(repository)
		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		// 网络恢复：同样的 payload 这次成功
		repository.onResolve = { Result.success(FakeScanRepository.defaultContent("成功了")) }
		val effects = collectEffects(vm)
		vm.dispatch(ScanIntent.OnRetryResolve)

		assertEquals("重试要重发同一段原文，不要求用户重新扫", listOf(videoCode, videoCode), repository.calls)
		val resolved = effects.filterIsInstance<ScanEffect.Resolved>().singleOrNull()
		assertTrue("重试成功后要跳转", resolved != null)
		assertNull("成功后错误要清掉", vm.uiState.value.error)
	}

	@Test
	fun `retry does nothing when there is no pending payload`() = runTest {
		// 没有待处理原文时按「重试」不该发一个空请求出去
		val repository = FakeScanRepository()
		val vm = ScanViewModel(repository)

		vm.dispatch(ScanIntent.OnRetryResolve)

		assertEquals("没有原文就不该发请求", emptyList<String>(), repository.calls)
	}

	@Test
	fun `continue scanning clears notice and kept payload`() = runTest {
		val repository = FakeScanRepository().apply {
			onResolve = { FakeScanRepository.networkFailure() }
		}
		val vm = ScanViewModel(repository)
		vm.dispatch(ScanIntent.OnCodeDetected(videoCode))

		vm.dispatch(ScanIntent.OnContinueScanning)

		val state = vm.uiState.value
		assertNull("错误要清掉，否则卡片立刻又弹回来（看起来像按钮坏了）", state.error)
		assertNull("识别结果也一起清", state.detected)
		assertNull("待处理原文一起清", state.pendingPayload)
	}

	// ───────────── 相册路径 ─────────────

	@Test
	fun `image without code becomes a pick again notice`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnImageWithoutCode())

		assertEquals(
			"相册图里没有码要落成 NoCodeInImage（出路是重新选图）",
			ScanError.NoCodeInImage(unreadable = false), vm.uiState.value.error
		)
	}

	@Test
	fun `unreadable image is distinguished from a codless image`() = runTest {
		val vm = ScanViewModel(FakeScanRepository())

		vm.dispatch(ScanIntent.OnImageWithoutCode(unreadable = true))

		assertEquals(
			"读不出来与图里没码对用户是两种原因，文案要说实话",
			ScanError.NoCodeInImage(unreadable = true), vm.uiState.value.error
		)
	}

	// ───────────────────────── 辅助 ─────────────────────────

	/**
	 * 收集一次性副作用。
	 *
	 * 必须**先订阅再 dispatch**：`uiEffect` 是 replay=0 的 SharedFlow，
	 * 没有订阅者时 emit 出去的值会被直接丢掉，晚订阅的测试会挂住等一个永远不来的事件。
	 * 用 UnconfinedTestDispatcher 是为了让收集立即开始，不必先 advanceUntilIdle。
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	private fun TestScope.collectEffects(vm: ScanViewModel): MutableList<ScanEffect> {
		val effects = mutableListOf<ScanEffect>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
			vm.uiEffect.collect { effects += it }
		}
		return effects
	}
}
