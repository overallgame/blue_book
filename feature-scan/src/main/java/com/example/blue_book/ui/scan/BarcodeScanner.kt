package com.example.blue_book.ui.scan

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * 从一帧画面或一张图片里解出二维码内容。
 *
 * **接口化的理由**（它是最易变的那一环，见设计方案 4.4 的稳定度表）：
 * - 识别库会换（ML Kit ↔ ZXing）、识别参数会调、将来可能加一维码 → 换实现只改 [MlKitBarcodeScanner] 一个文件
 * - 相机路径与相册路径**共用它** → 否则早晚出现「相机严、相册松」这种不一致
 * - 它天然不可单测（要相机/位图），但它的接口化让**解析层完全不碰它**，
 *   所以"它测不了"不会拖累可测覆盖率
 */
interface BarcodeScanner {

	/** 相机帧：调用方负责在返回后关闭 [image]（本方法内部不关，见实现注释）。无码返回空列表。 */
	suspend fun analyze(image: ImageProxy): List<String>

	/** 相册图片。无码返回空列表。 */
	suspend fun analyze(bitmap: Bitmap): List<String>

	/** 释放识别器占用的资源（离开页面时调用）。 */
	fun close()
}
