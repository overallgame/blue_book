package com.example.blue_book.ui.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.blue_book.domain.model.ScannedContent
import com.example.blue_book.feature_scan.databinding.ActivityScanBinding
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.scan.ScanTarget
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 扫一扫（独立 Activity，压在 MainActivity 之上，返回即回到点击它的页面）。
 *
 * 本页**不判登录**：入口 `mine_scan` 已经套了 `guardLogin`，那是既定的约束位置。
 *
 * 职责划分（设计方案 4.0 的关注点拆分）：
 * - **C1 相机与权限**（本类）：预览、权限、帧的生命周期
 * - **C2 识别**（[BarcodeScanner]）：可换库
 * - **C3 归类**（[com.example.blue_book.scan.ScanCodeFormat]）：纯函数
 * - **C4/C5 编排与状态机**（[ScanViewModel]）：不认识 CameraX 也不认识 ML Kit
 * - **"给什么出路"**（[toNotice]）：纯函数，失败矩阵的可测形态
 * - **"怎么调系统"**（本类的 [onNoticeAction]）：去设置、开浏览器、选图——不可测，也不必测
 *
 * 这一层刻意只做"平台动作"：凡是**判断**（该给哪个状态、该显示哪些按钮）都在别处，
 * 所以本类里的 `when` 都只翻译不决策。
 *
 * **相机释放不用手写 onPause**：`bindToLifecycle` 把用例绑到 Activity 生命周期上，
 * 切后台/来电时 CameraX 自动停掉采集，回前台自动恢复（设计方案 N4）。
 */
@AndroidEntryPoint
@Route(path = RoutePath.SCAN)
class ScanActivity : AppCompatActivity() {

	private lateinit var binding: ActivityScanBinding
	private val viewModel: ScanViewModel by viewModels()

	@Inject
	lateinit var scanner: BarcodeScanner

	private var cameraProvider: ProcessCameraProvider? = null

	/** 防止 onCreate 与 onResume 各起一次相机（`ProcessCameraProvider` 的 future 可被多次 addListener） */
	private var cameraStarting = false

	/**
	 * 持有分析用例，好在"校验期间"把它摘掉。
	 *
	 * 为什么需要：去重窗口只有 2 秒，而一次校验要几百毫秒——用户对着同一个码停留几秒，
	 * 分析器会继续喂帧、窗口一过就再发一次请求。**闸门要关在产生帧的那一侧**。
	 */
	private var analysis: ImageAnalysis? = null
	private val analysisExecutor by lazy { ContextCompat.getMainExecutor(this) }

	/** 分析器当前是否挂着。CameraX 1.3 没有 `hasAnalyzer()`，只能自己记（见 [applyAnalyzerState]） */
	private var analyzerAttached = false

	private val pickImage = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		// 相册选图取消：**静默**回扫码态（设计方案 7.2），一个字都不提示——
		// 用户主动取消不是错误，报一句"已取消"只会打扰他
		if (result.resultCode != RESULT_OK) return@registerForActivityResult
		val uri = result.data?.data ?: return@registerForActivityResult
		decodeFromGallery(uri)
	}

	private val requestCamera = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		// canAskAgain 只有平台知道：被拒之后系统是否还会弹框，决定按钮是「再次申请」还是「去设置」
		viewModel.dispatch(ScanIntent.OnCameraPermissionResult(granted, canAskAgain = canAskCameraAgain()))
		if (granted) startCamera()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityScanBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.scanToolbar.setNavigationOnClickListener { finish() }
		binding.scanPickGallery.setOnClickListener { startPickingImage() }
		collectEffects()
		collectState()
		ensureCameraPermission()
	}

	/**
	 * 从系统设置回来后权限可能已经变了——**重新问一次，别停在旧结论上**（R6 的闭环）。
	 *
	 * 这里刻意**不无条件重启相机**：正常的前后台切换由 `bindToLifecycle` 自动处理，
	 * 重复 startCamera 只会白白重建一次预览。
	 */
	override fun onResume() {
		super.onResume()
		if (hasCameraPermission()) {
			if (cameraProvider == null) startCamera()
		} else if (viewModel.uiState.value.phase is ScanPhase.PermissionDenied) {
			// 仍未授权：刷新 canAskAgain（用户可能刚在设置里改过"不再询问"）
			viewModel.dispatch(ScanIntent.OnCameraPermissionResult(false, canAskAgain = canAskCameraAgain()))
		}
	}

	/**
	 * 释放识别器。`BarcodeScanner` 刻意没绑成单例（见 `ScanModule` 注释）：
	 * 这里的 close() 只会关掉本页自己的实例。
	 */
	override fun onDestroy() {
		super.onDestroy()
		cameraProvider?.unbindAll()
		scanner.close()
	}

	// ───────────────────────── 相机与权限（C1）─────────────────────────

	private fun hasCameraPermission(): Boolean =
		ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
			PackageManager.PERMISSION_GRANTED

	/**
	 * 系统是否还会为我们弹权限框。
	 *
	 * 语义容易记反：`shouldShowRequestPermissionRationale` 返回 true 表示**该解释**，
	 * 也就是"还能再问"；返回 false 有两种情况——从没问过（第一次）与"不再询问"。
	 * 所以这里要先排除"从没问过"（已授权时也返回 false），只在**已被拒绝**的前提下当判据用。
	 */
	private fun canAskCameraAgain(): Boolean =
		androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
			this, Manifest.permission.CAMERA
		)

	private fun ensureCameraPermission() {
		if (hasCameraPermission()) {
			// 已授权也要报给状态机：否则页面会永远停在 CheckingPermission
			viewModel.dispatch(ScanIntent.OnCameraPermissionResult(true, canAskAgain = true))
			startCamera()
		} else {
			requestCamera.launch(Manifest.permission.CAMERA)
		}
	}

	private fun startCamera() {
		if (cameraStarting || cameraProvider != null) return
		cameraStarting = true
		val future = ProcessCameraProvider.getInstance(this)
		future.addListener({
			cameraStarting = false
			runCatching { future.get() }
				.onSuccess { provider ->
					cameraProvider = provider
					bindUseCases(provider)
				}
				.onFailure { error ->
					// 设备无相机 / 相机被占用：同样的降级，不崩。
					// 状态机里是**独立分支**（CameraUnavailable）——申请权限、翻设置都救不了它
					Log.w(TAG, "相机启动失败", error)
					viewModel.dispatch(ScanIntent.OnCameraUnavailable)
				}
		}, ContextCompat.getMainExecutor(this))
	}

	private fun bindUseCases(provider: ProcessCameraProvider) {
		val preview = Preview.Builder().build().also {
			// CameraX 1.3 的 Preview 只有 setSurfaceProvider、没有 getter，
			// 因此 Kotlin 不把它暴露成属性，只能显式调方法
			it.setSurfaceProvider(binding.scanPreview.surfaceProvider)
		}

		// KEEP_ONLY_LATEST：识别比采集慢，积压旧帧只会让画面里早就移走的码仍被处理
		val imageAnalysis = ImageAnalysis.Builder()
			.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
			.build()
			.also { it.setAnalyzer(analysisExecutor, createAnalyzer()) }
		analysis = imageAnalysis
		analyzerAttached = true

		provider.unbindAll()
		try {
			provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
			binding.scanPreview.visibility = View.VISIBLE
			binding.scanPlaceholder.visibility = View.GONE
			viewModel.dispatch(ScanIntent.OnCameraStarted)
		} catch (error: IllegalArgumentException) {
			// 没有后置相机等：降级而不是崩
			Log.w(TAG, "相机用例绑定失败", error)
			viewModel.dispatch(ScanIntent.OnCameraUnavailable)
		}
	}

	// ───────────────────────── 识别（C2 的驱动）─────────────────────────

	private fun createAnalyzer() = ImageAnalysis.Analyzer { image ->
		// 分析器是同步回调，识别是挂起的；在 lifecycleScope 里做，
		// 这样离开页面时未完成的识别会被取消。image 必须在处理完后关闭，否则后续帧拿不到。
		lifecycleScope.launch {
			try {
				scanner.analyze(image).firstOrNull()?.let { payload ->
					viewModel.dispatch(ScanIntent.OnCodeDetected(payload))
				}
			} catch (cancel: CancellationException) {
				// 取消必须透传：离开页面时 lifecycleScope 被取消，这是**正常流程**而不是失败；
				// 当成识别失败吞掉会污染日志，父作用域也观察不到取消。规则与 `UdfViewModel.runResult` 一致。
				throw cancel
			} catch (error: Throwable) {
				// 单帧识别失败不该影响后续帧：记日志继续
				Log.w(TAG, "单帧识别失败", error)
			} finally {
				image.close()
			}
		}
	}

	// ───────────────────────── 相册路径（与相机同一套解析）─────────────────────────

	private fun startPickingImage() {
		// 打开选图前先清掉上一次的提示：用户点了"重新选图"却取消时，
		// 页面应当回到干净的扫描态，而不是继续挂着"这张图里没有二维码"
		viewModel.dispatch(ScanIntent.OnContinueScanning)

		// 跳转走路由（项目约定），但用 ActivityResultLauncher 启动以便拿回结果。
		// 带 EXTRA_SKIP_CROP：扫码要的是原图本身——进裁剪页既多一步，
		// 又可能把二维码的静默区裁掉、重压缩后削弱识别率。
		val intent = TheRouter.build(RoutePath.IMAGE_PICKER)
			.withString(ExtraKeys.EXTRA_IMAGE_TAG, IMAGE_TAG_SCAN)
			.createIntent(this)
			.putExtra(ExtraKeys.EXTRA_SKIP_CROP, true)
		pickImage.launch(intent)
	}

	private fun decodeFromGallery(uri: Uri) {
		lifecycleScope.launch {
			val bitmap = withContext(Dispatchers.IO) {
				contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
			}
			if (bitmap == null) {
				// 读不出来与"图里没有码"是两回事，对用户的出路一样（换一张），
				// 但文案要说实话：否则用户会一直换图，而问题在文件本身
				viewModel.dispatch(ScanIntent.OnImageWithoutCode(unreadable = true))
				return@launch
			}

			// 用完**不 recycle**：ML Kit 的 InputImage 还包着这个 bitmap，
			// 回收一个还在用的 bitmap 会崩（trying to use a recycled bitmap）。一次性的页面交给 GC 即可。
			// 用显式 try/catch 而**不是** runCatching：后者也捕 Throwable，
			// 会把取消当成"识别失败"吞掉。
			val payloads = try {
				scanner.analyze(bitmap)
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Throwable) {
				Log.w(TAG, "识别失败", error)
				emptyList()
			}

			// 一张图里可能有多个码。取第一个：相册路径没有"继续扫"的语义，
			// 让用户重选一张比让他再等一次更直接。
			val payload = payloads.firstOrNull()
			if (payload == null) {
				viewModel.dispatch(ScanIntent.OnImageWithoutCode())
			} else {
				viewModel.dispatch(ScanIntent.OnCodeDetected(payload))
			}
		}
	}

	// ───────────────────────── 渲染：状态 → 界面 ─────────────────────────

	/**
	 * 状态是页面的唯一数据源：提示、按钮、取景引导全部由它推出。
	 *
	 * 两个收集器共用同一个 `StateFlow`，但第二个要 `distinctUntilChanged`——
	 * `StateFlow` 自己只在**值变了**（按 equals）时发射，而 `analyzing` 是从 phase 算出来的：
	 * 换了一个识别结果但 phase 没变时，state 变了、`analyzing` 没变，
	 * 不去重就会白挂一次分析器（每次 setAnalyzer 都重建分析器）。
	 */
	private fun collectState() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.uiState.collect(::render)
			}
		}
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.uiState
					.map { it.analyzing }
					.distinctUntilChanged()
					.collect(::applyAnalyzerState)
			}
		}
	}

	private fun render(state: ScanUiState) {
		// 取景引导：相机在跑、且没有别的事要说的时候才提示"把码放进来"
		binding.scanHint.visibility =
			if (state.analyzing && state.toNotice() == null) View.VISIBLE else View.GONE

		// 相机没起来时中间那片是空的，用一句话占位；起了相机就交给预览
		if (binding.scanPreview.visibility != View.VISIBLE) {
			binding.scanPlaceholder.visibility = View.VISIBLE
			binding.scanPlaceholder.text = when (state.phase) {
				ScanPhase.CheckingPermission -> "正在准备相机…"
				// 其余情况中间那句没有信息量：原因与出路都在下面的卡片里
				else -> ""
			}
		}

		val notice = state.toNotice()
		if (notice == null) {
			binding.scanNotice.visibility = View.GONE
			return
		}
		binding.scanNotice.visibility = View.VISIBLE
		binding.scanNoticeTitle.text = notice.title
		binding.scanNoticeContent.text = notice.content.orEmpty()
		binding.scanNoticeContent.visibility = if (notice.content == null) View.GONE else View.VISIBLE
		bindAction(binding.scanNoticePrimary, notice.primary, notice.content)
		bindAction(binding.scanNoticeSecondary, notice.secondary, notice.content)
	}

	/**
	 * 把动作绑到按钮。**文案也在这里给**（而不是布局里）：同一个动作在不同场景下说法不同
	 * （「再次申请」与「去设置」都是 `REQUEST_PERMISSION`/`OPEN_SETTINGS`，但 `PICK_IMAGE`
	 * 在权限态叫"从相册选图"、在"图里没有码"时叫"重新选图"）。
	 */
	private fun bindAction(view: TextView, action: NoticeAction?, content: String?) {
		if (action == null) {
			view.visibility = View.GONE
			return
		}
		view.visibility = View.VISIBLE
		view.text = labelOf(action)
		view.setOnClickListener { onNoticeAction(action, content) }
	}

	private fun labelOf(action: NoticeAction): String = when (action) {
		NoticeAction.RETRY -> "重试"
		NoticeAction.CONTINUE -> "继续扫"
		NoticeAction.PICK_IMAGE -> "从相册选图"
		NoticeAction.REQUEST_PERMISSION -> "再次申请"
		NoticeAction.OPEN_SETTINGS -> "去设置"
		NoticeAction.COPY -> "复制"
		NoticeAction.OPEN_BROWSER -> "用浏览器打开"
	}

	/** 动作 → 系统调用。这里是本类唯一"做事"的地方，判断已在 [toNotice] 里做完。 */
	private fun onNoticeAction(action: NoticeAction, content: String?) {
		when (action) {
			// 重试与继续扫都是状态变化，交回状态机（`pendingPayload` 是它记着的）
			NoticeAction.RETRY -> viewModel.dispatch(ScanIntent.OnRetryResolve)
			NoticeAction.CONTINUE -> viewModel.dispatch(ScanIntent.OnContinueScanning)

			// 重新选图：面板上写的是"从相册选图/重新选图"，动作是同一个
			NoticeAction.PICK_IMAGE -> startPickingImage()

			NoticeAction.REQUEST_PERMISSION -> requestCamera.launch(Manifest.permission.CAMERA)

			// 系统不再弹框时唯一的出路：跳到本应用的权限设置页
			NoticeAction.OPEN_SETTINGS -> openAppSettings()

			NoticeAction.COPY -> copyToClipboard(content)

			// 只可能是解析器认过的 http(s)：外部链接的"打开"必须是**用户手点**的，
			// 扫到就自动打开等于把跳转权交给了二维码的制作者（设计方案 N2）
			NoticeAction.OPEN_BROWSER -> openInBrowser(content)
		}
	}

	private fun openAppSettings() {
		val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
			.setData(Uri.fromParts("package", packageName, null))
		startActivitySafely(intent, "无法打开系统设置")
	}

	private fun copyToClipboard(text: String?) {
		if (text.isNullOrBlank()) return
		val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText("扫一扫", text))
		toast("已复制")
	}

	private fun openInBrowser(url: String?) {
		if (url.isNullOrBlank()) return
		// 再验一次 scheme：这段文本来自不可信的二维码。调用点今天只会传解析器认过的
		// http(s)，但 ACTION_VIEW 对 `intent:`/`market:`/厂商自定义 scheme 也会照单全收——
		// 未来某次改动把 OPEN_BROWSER 接到别的内容上，就会变成"扫码即唤起任意应用"。
		// 一道 scheme 白名单比"相信调用点"便宜。
		val safe = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
		if (!safe) {
			Log.w(TAG, "拒绝用浏览器打开非 http(s) 内容")
			return
		}
		startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "没有可用的浏览器")
	}

	private fun startActivitySafely(intent: Intent, failureMessage: String) {
		try {
			startActivity(intent)
		} catch (error: ActivityNotFoundException) {
			// 设备上确实可能没有浏览器/设置页被厂商裁掉：不能因此崩掉扫码页
			Log.w(TAG, failureMessage, error)
			toast(failureMessage)
		}
	}

	/**
	 * 校验期间暂停帧分析。
	 *
	 * `clearAnalyzer()` / `setAnalyzer()` 都是 CameraX 的线程安全操作，可以直接在
	 * 收集状态的协程里调用；相机**预览照常显示**——只停分析，不停采集，画面不会卡住。
	 *
	 * [analyzerAttached] 是必要的：CameraX 1.3 的 `ImageAnalysis` **没有 `hasAnalyzer()`**
	 * （只有 `setAnalyzer` / `clearAnalyzer`），不自己记着就会在校验期间反复 clear，
	 * 或反复 setAnalyzer（每次 set 都重建分析器）。
	 */
	private fun applyAnalyzerState(analyzing: Boolean) {
		val target = analysis ?: return
		if (analyzing == analyzerAttached) return
		analyzerAttached = analyzing
		if (analyzing) {
			target.setAnalyzer(analysisExecutor, createAnalyzer())
		} else {
			target.clearAnalyzer()
		}
	}

	// ───────────────────────── 副作用 ─────────────────────────

	private fun collectEffects() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.uiEffect.collect { effect ->
					when (effect) {
						is ScanEffect.Detected -> Log.d(TAG, "识别到：${describeForLog(effect.target)}")
						// 校验成功才跳：**先 resolve 成功再跳**（设计方案 N3），
						// 失败时停在原页并给出原因，用户不会被推进一个打不开的页面
						is ScanEffect.Resolved -> jumpThenFinish(effect.content)
					}
				}
			}
		}
	}

	/**
	 * 跳转目标页并关掉扫码页。
	 *
	 * `finish()` 之后返回直接回「我的」页——**不留下一个已经扫过的扫码页**
	 * （否则用户返回时会看到一个还开着相机的页面，还得再返回一次）。
	 */
	private fun jumpThenFinish(content: ScannedContent) {
		Log.d(TAG, "校验成功：${content.kind} #${content.targetId} 《${content.title}》")
		openScannedContent(this, content)
		finish()
	}

	/**
	 * 日志描述。
	 *
	 * **登录票据刻意不打内容**：设计方案 4.1 约定票据"不缓存、不打日志"——
	 * 它是能换登录态的一次性凭据，落到 logcat 就等于把凭据落盘。
	 */
	private fun describeForLog(target: ScanTarget): String = when (target) {
		is ScanTarget.InternalCode -> "站内码 [${target.raw}]"
		is ScanTarget.ExternalUrl -> "外部链接 [${target.url}]"
		is ScanTarget.PlainText -> "纯文本 [${target.text}]"
		is ScanTarget.LoginTicket -> "登录票据（内容不落日志）"
	}

	private fun toast(message: String) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
	}

	private companion object {
		const val TAG = "ScanActivity"
		const val IMAGE_TAG_SCAN = "scan"
	}
}
