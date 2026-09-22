package com.example.blue_book.ui.scan

import com.example.blue_book.domain.model.ScannedContent
import com.example.blue_book.scan.ScanTarget
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

/**
 * 用户操作与平台回调。
 *
 * 注意 [OnCameraPermissionResult] / [OnCameraUnavailable] / [OnCameraStarted] 这三个：
 * **权限与相机是平台事实，不是业务判断**——Activity 负责问系统、拿到结论后报给状态机，
 * 由状态机决定"这意味着什么、用户有什么出路"。反过来（在 Activity 里 if/else 决定 UI）
 * 就是把失败矩阵拆成两处，测不到也不会有单一真相。
 */
sealed interface ScanIntent : UiIntent {

	/**
	 * 解码出一段内容。**相机帧与相册图片都走这一个入口**——两条路径共用后续处理。
	 */
	data class OnCodeDetected(val payload: String) : ScanIntent

	/**
	 * 相册里选的那张图没能给出内容（相机路径不会产生它：没解出内容就不会回调）。
	 *
	 * [unreadable] 区分两种原因：图根本读不出来（文件坏了/不是图片），
	 * 还是图能读但没有码。**它只影响文案，不影响出路**（都是「重新选图」+「继续扫」），
	 * 所以是一个布尔而不是新分支——原则是"不同取值导致不同**行为**才必须显式成 sealed"（5.1）。
	 */
	data class OnImageWithoutCode(val unreadable: Boolean = false) : ScanIntent

	/**
	 * 相机权限的最终结论。
	 *
	 * @param granted 是否已授权
	 * @param canAskAgain 还能不能再弹系统对话框。**由 Activity 用
	 *   `shouldShowRequestPermissionRationale` 问出来**——只有平台知道这个答案，
	 *   而它直接决定按钮是「再次申请」还是「去设置」。
	 */
	data class OnCameraPermissionResult(val granted: Boolean, val canAskAgain: Boolean) : ScanIntent

	/** 相机不可用：无后置相机、被其它应用占用、打开失败等。**再申请权限也没用**，所以是独立状态。 */
	data object OnCameraUnavailable : ScanIntent

	/** 相机已挂上并开始分析（预览可用）。 */
	data object OnCameraStarted : ScanIntent

	/** 网络失败后重试上一次的原文；不重新扫码。 */
	data object OnRetryResolve : ScanIntent

	/** 「继续扫」：清掉提示与本条结果，回到干净的扫描态。 */
	data object OnContinueScanning : ScanIntent
}

/**
 * 当前阶段：**互斥且穷举**（设计方案 5.3）。
 *
 * 为什么要 sealed 而不是几个 Boolean：不同阶段允许的操作不同（预览该不该开、按钮该不该显示、
 * 分析器该不该跑），而 `isScanning && isResolving` 这种非法组合用布尔根本拦不住。
 */
sealed interface ScanPhase {

	/** 还在问权限/等相机起来。 */
	data object CheckingPermission : ScanPhase

	/**
	 * 权限被拒。[canAskAgain] 来自系统，决定按钮是「再次申请」还是「去设置」——
	 * 这是**平台给的信息**，所以显式带在状态里，而不是让 UI 去猜或自己再问一次。
	 */
	data class PermissionDenied(val canAskAgain: Boolean) : ScanPhase

	/**
	 * 相机不可用（无后置相机 / 被占用 / 打开失败）。
	 *
	 * ★ 与 [PermissionDenied] **分成两个分支**是按本设计的 5.1 原则来的：
	 * "某个状态的不同取值会导致不同的行为，它就必须是显式字段"。
	 * 设备没有相机时去申请权限、去设置里翻权限都是徒劳——**能给的出路只有相册**，
	 * 这与"权限被拒"是两套按钮。都塞进 `PermissionDenied` 就只能靠文案区分，
	 * 而文案拦不住"给了一个按了没用的按钮"。
	 */
	data object CameraUnavailable : ScanPhase

	/** 相机在跑，等待识别。 */
	data object Scanning : ScanPhase

	/** 正在请服务端校验（此时分析器应当停，避免对同一个码连发请求）。 */
	data object Resolving : ScanPhase
}

/**
 * 失败原因：**每个分支就是一条出路**（设计方案 7.2 的失败矩阵）。
 *
 * 这是它必须 sealed 的理由：如果只有一个 `errorMessage: String`，
 * UI 只能靠字符串猜该给「重试」还是「重新选图」还是「去设置」。
 */
sealed interface ScanError {

	/**
	 * 出路：重试（保留已扫到的原文，用户不必重扫）。
	 *
	 * [reason] 带的是 `NetworkException` 那句中文（"请求超时，请检查网络后重试" /
	 * "无法连接到服务器，请检查网络或服务是否可用"）——它**比笼统的"网络失败"更有用**：
	 * 超时与"服务没起来"的排查方向完全不同，而这两种原因客户端是分得开的。
	 * 它也进 logcat：真机排查时那是唯一能看到原因的地方。
	 */
	data class Network(val reason: String) : ScanError

	/** 出路：仅提示。message 是**服务端返回的中文原文**（"视频不存在或已被删除"等）。 */
	data class Rejected(val message: String) : ScanError

	/** 出路：重新选图（相机可能永远解不出这张图里的码，让用户换一张）。 */
	data class NoCodeInImage(val unreadable: Boolean = false) : ScanError
}

/**
 * 页面状态。**页面的唯一数据源**：所有提示与按钮都由它推出来（见 [toNotice]）。
 *
 * [analyzing] 是**算出来的**而不是存下来的：它只能由 phase 决定
 * （只有 [ScanPhase.Scanning] 才该分析）。存成独立字段就会允许
 * "phase 是 Resolving 但 analyzing 还是 true"这种非法组合——正是 5.2 批评过的写法；
 * 而它又是 UI 要用的（决定相机分析器的挂/摘），所以做成计算属性：既有这个信息，又没有第二个真相。
 */
data class ScanUiState(
	val phase: ScanPhase = ScanPhase.CheckingPermission,
	/** 最近一次识别到的内容，null 表示还没扫到 */
	val detected: ScanTarget? = null,
	/** 已扫到、**还没处理成功**的原文：网络失败时保留，用户重试不必重扫（N8） */
	val pendingPayload: String? = null,
	/** 失败原因；null 表示当前没有错误 */
	val error: ScanError? = null
) : UiState {

	/** UI 据此暂停/恢复帧分析。只有扫描态才分析。 */
	val analyzing: Boolean get() = phase is ScanPhase.Scanning
}

sealed interface ScanEffect : UiEffect {

	/** 识别到内容（一次性）。日志用：状态里的 `detected` 无法区分"新扫到"与"重新渲染"。 */
	data class Detected(val target: ScanTarget) : ScanEffect

	/**
	 * 校验成功，跳转目标页然后 finish 掉本页。
	 *
	 * 为什么是 effect 而不是状态：跳转是**一次性的动作**，不是"页面长什么样"。
	 * 放进状态会在旋转/重建后重新触发一次跳转。
	 */
	data class Resolved(val content: ScannedContent) : ScanEffect
}
