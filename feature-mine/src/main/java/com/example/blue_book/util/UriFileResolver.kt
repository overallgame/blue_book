package com.example.blue_book.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UriFileResolver @Inject constructor(
	@ApplicationContext private val context: Context
) {
	fun mimeTypeOf(uri: Uri): String? {
		return context.contentResolver.getType(uri)
	}

	/** 复制到缓存目录（IO 线程执行，避免大图拷贝阻塞主线程） */
	suspend fun copyToCacheFile(uri: Uri, filenamePrefix: String): File? =
		withContext(Dispatchers.IO) {
			try {
				cleanStaleCacheFiles(filenamePrefix)
				val tempFile = File.createTempFile(filenamePrefix, resolveSuffix(uri), context.cacheDir)
				context.contentResolver.openInputStream(uri)?.use { input ->
					FileOutputStream(tempFile).use { output ->
						input.copyTo(output)
					}
				}
				tempFile
			} catch (e: IOException) {
				null
			}
		}

	/**
	 * 临时文件后缀：优先 MIME，其次 Uri 自带扩展名。
	 * 裁剪页产物是 JPEG 且走 file://（无 MIME、无扩展名），统一按 .jpg 处理，
	 * 否则会以 application/octet-stream 上传。
	 */
	private fun resolveSuffix(uri: Uri): String {
		val fromMime = when (mimeTypeOf(uri)?.lowercase(Locale.ROOT)) {
			"image/jpeg", "image/jpg" -> ".jpg"
			"image/png" -> ".png"
			"image/webp" -> ".webp"
			"image/gif" -> ".gif"
			else -> null
		}
		if (fromMime != null) return fromMime
		return when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
			"png" -> ".png"
			"webp" -> ".webp"
			"gif" -> ".gif"
			else -> ".jpg"
		}
	}

	/** 清理本类产生的历史临时文件（超过 1 小时），避免缓存目录累积 */
	private fun cleanStaleCacheFiles(prefix: String) {
		val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
		context.cacheDir.listFiles { f -> f.name.startsWith(prefix) }
			?.filter { it.lastModified() < cutoff }
			?.forEach { it.delete() }
	}

	fun guessMimeType(file: File): String {
		val ext = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
		return when (ext) {
			"jpg", "jpeg" -> "image/jpeg"
			"png" -> "image/png"
			"webp" -> "image/webp"
			"gif" -> "image/gif"
			else -> "application/octet-stream"
		}
	}
}
