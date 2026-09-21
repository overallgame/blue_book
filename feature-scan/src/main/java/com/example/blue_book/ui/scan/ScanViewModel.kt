package com.example.blue_book.ui.scan

import com.example.blue_book.domain.PayloadDedup
import com.example.blue_book.scan.ScanCodeFormat
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 扫一扫的编排。
 *
 * 第 3 步做两件事：**去重**（相机每秒回调几十次，同一个码会被反复解出）与**归类**
 * （[ScanCodeFormat.parse]），然后抛一次性事件。
 * 后续：第 4 步接后端 `/scan/resolve`、第 5 步接状态机与失败路径。
 *
 * ★ 构造签名**故意为空**：没有任何下层或平台依赖，所以这个类可以在纯 JVM 上单测
 * （`tools/check_viewmodel_layer.py` 管的就是这件事）。相机与识别器都在 UI 层，
 * 由 Activity 拿帧/拿图后把**字符串**喂进来——ViewModel 不认识 CameraX，也不认识 ML Kit。
 */
@HiltViewModel
class ScanViewModel @Inject constructor() :
	UdfViewModel<ScanIntent, ScanUiState, ScanEffect>(ScanUiState()) {

	/** 时间窗去重。策略与测试见 `domain/PayloadDedup.kt` */
	private val dedup = PayloadDedup()

	override suspend fun handleIntent(intent: ScanIntent) {
		when (intent) {
			is ScanIntent.OnCodeDetected -> onCodeDetected(intent.payload)
		}
	}

	private suspend fun onCodeDetected(payload: String) {
		// 相机路径会以每秒几十次的频率重复解出同一个码；相册路径一般是单次，
		// 走同一个入口就一起受保护了，不需要两条路径各写一套去重
		if (!dedup.shouldHandle(payload, System.currentTimeMillis())) return

		val target = ScanCodeFormat.parse(payload)
		setState { copy(detected = target) }
		sendEffect(ScanEffect.Detected(target))
	}
}
