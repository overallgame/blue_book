package com.example.blue_book.ui.scan

import com.example.blue_book.scan.ScanTarget
import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState

sealed interface ScanIntent : UiIntent {

	/** 解码出一段内容。**相机帧与相册图片都走这一个入口**——两条路径共用后续处理。 */
	data class OnCodeDetected(val payload: String) : ScanIntent
}

/**
 * 第 3 步页面还没有需要跨重建保留的状态（识别结果是一次性事件，走 [ScanEffect.Detected]）。
 * 权限态与阶段状态在第 5 步接状态机时加进来——那时它才会有多个分支。
 */
data class ScanUiState(
	/** 最近一次识别到的内容（第 5 步用它驱动 UI），null 表示还没扫到 */
	val detected: ScanTarget? = null
) : UiState

sealed interface ScanEffect : UiEffect {

	/** 识别到内容（一次性）。第 3 步只打日志，第 5 步换成校验、跳转与提示。 */
	data class Detected(val target: ScanTarget) : ScanEffect
}
