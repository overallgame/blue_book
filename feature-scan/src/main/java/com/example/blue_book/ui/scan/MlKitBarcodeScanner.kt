package com.example.blue_book.ui.scan

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [BarcodeScanner] 的 ML Kit 实现。
 *
 * ★ **本文件是全项目唯一 import ML Kit 的地方**。换识别库（比如换 ZXing）只改这里，
 * 其余代码只认 `BarcodeScanner` 接口。
 *
 * 没有用 `kotlinx-coroutines-play-services` 的 `Task.await()`：为了一个 await 多引一个依赖不划算，
 * 这里手写 `suspendCancellableCoroutine` 包一层，行为等价且没有额外依赖。
 */
class MlKitBarcodeScanner @Inject constructor() : BarcodeScanner {

	// 延迟创建：识别器初始化有成本，而相册路径可能一直用不到相机路径
	private val client by lazy { BarcodeScanning.getClient() }

	override suspend fun analyze(image: ImageProxy): List<String> {
		// image.image 在某些输出格式下可能为 null（CameraX 的约定），此时没有可识别的帧
		val mediaImage = image.image ?: return emptyList()
		return analyze(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
	}

	override suspend fun analyze(bitmap: Bitmap): List<String> =
		analyze(InputImage.fromBitmap(bitmap, 0))

	override fun close() {
		client.close()
	}

	private suspend fun analyze(input: InputImage): List<String> =
		suspendCancellableCoroutine { continuation ->
			client.process(input)
				.addOnSuccessListener { barcodes ->
					// rawValue 是码里的原始文本；url/其它结构化字段我们不用——
					// 内容怎么解释由 ScanCodeFormat 与后端决定，识别层只管把原文取出来
					continuation.resume(barcodes.mapNotNull { it.rawValue })
				}
				.addOnFailureListener { error ->
					continuation.resumeWithException(error)
				}
		}
}
