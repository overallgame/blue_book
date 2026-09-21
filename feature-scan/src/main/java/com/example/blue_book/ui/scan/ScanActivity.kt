package com.example.blue_book.ui.scan

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
 * - 本类负责 C1（相机/权限，3b 接）与"把帧/图交给识别器"这一步
 * - 识别器是 C2（[BarcodeScanner]，可换库）
 * - 归类是 C3（[com.example.blue_book.scan.ScanCodeFormat]，纯函数）
 * - 编排是 C4/C5（[ScanViewModel]，它不认识 CameraX 也不认识 ML Kit）
 */
@AndroidEntryPoint
@Route(path = RoutePath.SCAN)
class ScanActivity : AppCompatActivity() {

	private lateinit var binding: ActivityScanBinding
	private val viewModel: ScanViewModel by viewModels()

	@Inject
	lateinit var scanner: BarcodeScanner

	private val pickImage = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode != RESULT_OK) return@registerForActivityResult
		val uri = result.data?.data ?: return@registerForActivityResult
		decodeFromGallery(uri)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityScanBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.scanToolbar.setNavigationOnClickListener { finish() }
		binding.scanPickGallery.setOnClickListener { launchImagePicker() }
		collectEffects()
	}

	/**
	 * 释放识别器。`BarcodeScanner` 刻意没绑成单例（见 `ScanModule` 注释）：
	 * 这里的 close() 只会关掉本页自己的实例。
	 */
	override fun onDestroy() {
		super.onDestroy()
		scanner.close()
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
			val payloads = runCatching { scanner.analyze(bitmap) }.getOrElse { error ->
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
