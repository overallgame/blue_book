package com.example.blue_book.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.example.blue_book.feature_scan.databinding.ActivityScanBinding
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.scan.ScanTarget
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 扫一扫（独立 Activity，压在 MainActivity 之上，返回即回到点击它的页面）。
 *
 * 本页**不判登录**：入口 `mine_scan` 已经套了 `guardLogin`，那是既定的约束位置。
 *
 * 职责划分（见设计方案 4.0 的关注点拆分）：
 * - 本类负责 C1（相机预览、权限、帧的生命周期）与"把帧/图交给识别器"
 * - 识别器是 C2（[BarcodeScanner]，可换库）
 * - 归类是 C3（[com.example.blue_book.scan.ScanCodeFormat]，纯函数）
 * - 编排是 C4/C5（[ScanViewModel]），它不认识 CameraX 也不认识 ML Kit
 *
 * **相机释放不用手写 onPause**：`bindToLifecycle` 把用例绑到 Activity 生命周期上，
 * 切后台/来电时 CameraX 自动停掉采集，回前台自动恢复——这正是用它而不是手动管相机的理由。
 */
@AndroidEntryPoint
@Route(path = RoutePath.SCAN)
class ScanActivity : AppCompatActivity() {

	private lateinit var binding: ActivityScanBinding
	private val viewModel: ScanViewModel by viewModels()

	@Inject
	lateinit var scanner: BarcodeScanner

	private var cameraProvider: ProcessCameraProvider? = null

	private val pickImage = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode != RESULT_OK) return@registerForActivityResult
		val uri = result.data?.data ?: return@registerForActivityResult
		decodeFromGallery(uri)
	}

	private val requestCamera = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		if (granted) startCamera() else showPermissionDenied()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityScanBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.scanToolbar.setNavigationOnClickListener { finish() }
		binding.scanPickGallery.setOnClickListener { launchImagePicker() }
		collectEffects()
		ensureCameraPermission()
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

	// ───────────────────────── 相机（3b）─────────────────────────

	private fun ensureCameraPermission() {
		val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
			PackageManager.PERMISSION_GRANTED
		if (granted) startCamera() else requestCamera.launch(Manifest.permission.CAMERA)
	}

	/**
	 * 权限被拒时的降级：**不退出页面**，保留相册入口。
	 *
	 * 这正是方案里把"相册识别"当成必须项的原因——否则相机权限一被拒，整个扫码功能就死了。
	 * （完整的降级 UX——区分"还能再问一次"与"只能去设置"、加跳设置按钮——在第 5 步的状态机里做。）
	 */
	private fun showPermissionDenied() {
		binding.scanPreview.visibility = View.GONE
		binding.scanPlaceholder.text = "没有相机权限，无法实时扫码\n可点下方「从相册选择图片」识别图里的二维码"
	}

	private fun startCamera() {
		val future = ProcessCameraProvider.getInstance(this)
		future.addListener({
			runCatching { future.get() }
				.onSuccess { provider ->
					cameraProvider = provider
					bindUseCases(provider)
				}
				.onFailure { error ->
					// 设备无相机 / 相机被占用：同样的降级，不崩
					Log.w(TAG, "相机启动失败", error)
					showPermissionDenied()
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
		val analysis = ImageAnalysis.Builder()
			.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
			.build()
			.also { it.setAnalyzer(ContextCompat.getMainExecutor(this), createAnalyzer()) }

		provider.unbindAll()
		try {
			provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
			binding.scanPreview.visibility = View.VISIBLE
			binding.scanPlaceholder.visibility = View.GONE
		} catch (error: IllegalArgumentException) {
			// 没有后置相机等：降级而不是崩
			Log.w(TAG, "相机用例绑定失败", error)
			showPermissionDenied()
		}
	}

	private fun createAnalyzer() = ImageAnalysis.Analyzer { image ->
		// 分析器是同步回调，识别是挂起的；在 lifecycleScope 里做，
		// 这样离开页面时未完成的识别会被取消。image 必须在处理完后关闭，否则后续帧拿不到。
		lifecycleScope.launch {
			try {
				scanner.analyze(image).firstOrNull()?.let { payload ->
					viewModel.dispatch(ScanIntent.OnCodeDetected(payload))
				}
			} catch (cancel: CancellationException) {
				// 取消必须透传。离开页面时 lifecycleScope 被取消，这是**正常流程**而不是失败——
				// 真机实测踩到：原写法 catch(Throwable) 把 JobCancellationException 记成了
				// W 级「单帧识别失败」，既污染日志，又让协程"正常完成"、父作用域观察不到取消。
				// 规则与 `UdfViewModel.runResult` 一致。
				throw cancel
			} catch (error: Throwable) {
				// 单帧识别失败不该影响后续帧：记日志继续
				Log.w(TAG, "单帧识别失败", error)
			} finally {
				image.close()
			}
		}
	}

	// ─────────────── 相册路径（3a 先做它：同一套解码，但可以确定性验证）───────────────

	private fun launchImagePicker() {
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
				toast("无法读取所选图片")
				return@launch
			}

			// 用完**不 recycle**：ML Kit 的 InputImage 包着这个 bitmap，
			// 而"回收了还在用的 bitmap"正是本项目裁剪页踩过的坑（trying to use a recycled bitmap）。
			// 一次性的页面让 GC 处理即可。
			// 用显式 try/catch 而**不是** runCatching：后者也捕 Throwable，
			// 会把取消当成"识别失败"吞掉（`PublishViewModel` 的注释里对同一坑有说明）。
			val payloads = try {
				scanner.analyze(bitmap)
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Throwable) {
				Log.w(TAG, "识别失败", error)
				emptyList()
			}

			// 一张图里可能有多个码。第 3 步只取第一个，多码策略留到第 5 步
			val payload = payloads.firstOrNull()
			if (payload == null) {
				toast("这张图里没有识别到二维码")
			} else {
				viewModel.dispatch(ScanIntent.OnCodeDetected(payload))
			}
		}
	}

	// ─────────────── 副作用：第 3 步只打日志 ───────────────

	private fun collectEffects() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.uiEffect.collect { effect ->
					when (effect) {
						is ScanEffect.Detected -> Log.d(TAG, "识别到：${describeForLog(effect.target)}")
					}
				}
			}
		}
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
