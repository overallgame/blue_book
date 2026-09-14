package com.example.blue_book.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * 裁剪用采样解码（长边目标 = 屏幕长边×2，上限 2560）+ EXIF 方向校正。
 *
 * **可在后台线程调用**：只读 Context 与 Uri，不触碰任何 View 状态。
 * 大图上的「bounds 解码 → 完整解码 → 再开一次流读 EXIF」是数百毫秒级的主线程阻塞。
 */
internal fun decodeSampledForCrop(context: Context, uri: Uri): Bitmap? {
	val resolver = context.contentResolver
	val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
	resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
	if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

	val dm = context.resources.displayMetrics
	val target = minOf(maxOf(dm.widthPixels, dm.heightPixels) * 2, 2560)
	var sample = 1
	while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) {
		sample *= 2
	}

	val opts = BitmapFactory.Options().apply { inSampleSize = sample }
	val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
		?: return null
	return applyExifOrientation(context, decoded, uri)
}

/** EXIF 方向校正：竖拍照片不躺倒。方向为 NORMAL 时原样返回（不产生副本） */
private fun applyExifOrientation(context: Context, bmp: Bitmap, uri: Uri): Bitmap {
	val orientation = try {
		context.contentResolver.openInputStream(uri)?.use { input ->
			ExifInterface(input).getAttributeInt(
				ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
			)
		} ?: ExifInterface.ORIENTATION_NORMAL
	} catch (_: Throwable) {
		ExifInterface.ORIENTATION_NORMAL
	}
	val matrix = Matrix()
	when (orientation) {
		ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
		ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
		ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
		ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
		ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
		ExifInterface.ORIENTATION_TRANSPOSE -> {
			matrix.postRotate(90f)
			matrix.postScale(-1f, 1f)
		}

		ExifInterface.ORIENTATION_TRANSVERSE -> {
			matrix.postRotate(270f)
			matrix.postScale(-1f, 1f)
		}

		else -> return bmp
	}
	val oriented = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
	// createBitmap 在"整图 + 等效单位矩阵"等情况下可能返回同一实例，故按身份判断后再回收
	if (oriented !== bmp) bmp.recycle()
	return oriented
}
