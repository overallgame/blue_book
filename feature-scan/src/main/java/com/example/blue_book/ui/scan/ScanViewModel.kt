package com.example.blue_book.ui.scan

import com.example.blue_book.domain.PayloadDedup
import com.example.blue_book.domain.repository.ScanRepository
import com.example.blue_book.domain.repository.ScanResolveFailure
import com.example.blue_book.scan.ScanCodeFormat
import com.example.blue_book.scan.ScanTarget
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 扫一扫的状态机（设计方案 7.1）。
 *
 * ```
 *  进入 → CheckingPermission
 *            ├─ 授予 → Scanning ──解出字符串──→ Resolving
 *            │            ↑                        ├─ 成功 → 跳转 → finish()
 *            │            └──── 继续扫 ←──── 失败 ──┘─ 失败 → error（停在本页 + 出路）
 *            └─ 拒绝 → PermissionDenied（再次申请/去设置 + 从相册选图）
 * ```
 *
 * 分工：本类只知道"发生了什么、现在什么状态"；**画什么、给哪些按钮**是
 * `ScanNotice`（纯函数，可穷举测试）；**怎么调系统**是 Activity。
 * 三者分开的直接好处是失败矩阵（7.2 的 11 行）能在纯 JVM 上逐行断言，
 * 而"给没给对的出路"这件事**只由状态决定**，不会在 Activity 里再散一份。
 *
 * 构造签名里只有一个接口 [ScanRepository]：没有 RemoteDataSource、没有 Context、
 * 没有 ApiGateway，所以它能在纯 JVM 上测（`tools/check_viewmodel_layer.py` 管的就是这件事）。
 * 相机与识别器都在 UI 层：Activity 拿帧/拿图后把**字符串**喂进来。
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
	private val scanRepository: ScanRepository
) : UdfViewModel<ScanIntent, ScanUiState, ScanEffect>(ScanUiState()) {

	/** 时间窗去重。策略与测试见 `domain/PayloadDedup.kt` */
	private val dedup = PayloadDedup()

	override suspend fun handleIntent(intent: ScanIntent) {
		when (intent) {
			is ScanIntent.OnCodeDetected -> onCodeDetected(intent.payload)
			is ScanIntent.OnImageWithoutCode ->
				setState { copy(error = ScanError.NoCodeInImage(unreadable = intent.unreadable)) }

			is ScanIntent.OnCameraPermissionResult -> onPermissionResult(intent)
			ScanIntent.OnCameraUnavailable -> setState { copy(phase = ScanPhase.CameraUnavailable) }
			ScanIntent.OnCameraStarted -> setState { copy(phase = ScanPhase.Scanning) }

			ScanIntent.OnRetryResolve -> retry()
			ScanIntent.OnContinueScanning -> setState {
				// detected 与 pendingPayload 一起清：否则「继续扫」之后旧的结果还在，
				// 卡片会立刻又弹回来（看起来像按钮坏了）
				copy(detected = null, pendingPayload = null, error = null)
			}
		}
	}

	// ───────────── 权限与相机（平台事实 → 状态）─────────────

	private fun onPermissionResult(intent: ScanIntent.OnCameraPermissionResult) {
		setState {
			copy(
				phase = if (intent.granted) ScanPhase.Scanning
				else ScanPhase.PermissionDenied(canAskAgain = intent.canAskAgain),
				// 授权与否都与"上一次的失败原因"无关：换到新状态时清掉，
				// 否则"权限被拒"的卡片会压在"网络失败"上面（或反过来，看谁先写）
				error = null
			)
		}
	}

	// ───────────── 识别 → 校验 ─────────────

	private suspend fun onCodeDetected(payload: String) {
		// 相机每秒回调几十次、同一个码会被反复解出；相册路径一般是单次。
		// 两条路径共用这一个入口，就不需要各写一套去重。
		if (!dedup.shouldHandle(payload, System.currentTimeMillis())) return

		val target = ScanCodeFormat.parse(payload)
		// 新的一次识别：清掉上一次的错误（否则"码已失效"的卡片会盖在这次的新结果上）
		setState { copy(detected = target, error = null) }
		sendEffect(ScanEffect.Detected(target))

		// 只有站内码要联网。其余三类（外部链接/纯文本/登录票据）的处置与网络无关，
		// 别为一个纯文本码去打扰服务端（设计方案 6.3）
		val code = target as? ScanTarget.InternalCode ?: return
		resolve(code.raw)
	}

	/**
	 * 交服务端校验。
	 *
	 * 传的是**原始字符串**而不是本地解析出来的 id：判据留在服务端，
	 * 后端将来加码格式时老版本 App 也能正确工作（设计方案 6.2）。
	 */
	private suspend fun resolve(raw: String) {
		runResult(
			onStart = {
				// pendingPayload 先记下：**网络失败时它是"不用重扫"的唯一依据**（N8）
				setState { copy(phase = ScanPhase.Resolving, pendingPayload = raw, error = null) }
			},
			call = { scanRepository.resolve(raw) },
			onSuccess = { content ->
				setState { copy(phase = ScanPhase.Scanning, pendingPayload = null, error = null) }
				sendEffect(ScanEffect.Resolved(content))
			},
			onFailure = { error -> setState { copy(phase = ScanPhase.Scanning, error = error.toScanError()) } }
		)
	}

	/** 网络失败后的重试：用保留的原文重发，不要求用户重新对准二维码。 */
	private suspend fun retry() {
		val payload = uiState.value.pendingPayload ?: return
		resolve(payload)
	}

	/**
	 * 失败 → 状态里的出路分类。
	 *
	 * 翻译在**这一层**做：仓库给的是 `ScanResolveFailure`（数据层的分类），
	 * 状态里要的是 `ScanError`（"给用户看的原因 + 出路"）。两者分开是因为
	 * 前者关心"是不是网络问题"，后者关心"UI 该给哪个按钮"——它们今天一一对应，
	 * 但理由不同（例如将来把"码已失效"单独拆出来给"重新生成"的出路，只动后者）。
	 *
	 * 认不出的 Throwable 归为 [ScanError.Network]（**可重试**）：
	 * 与其让用户看到一句"未知错误"又无路可走，不如让他重试一次。
	 */
	private fun Throwable.toScanError(): ScanError {
		val failure = this as? ScanResolveFailure
		val text = message?.takeIf { it.isNotBlank() }
		return if (failure is ScanResolveFailure.Rejected) {
			ScanError.Rejected(text ?: "校验失败，请重试")
		} else {
			ScanError.Network(text ?: "网络连接失败，请重试")
		}
	}
}
