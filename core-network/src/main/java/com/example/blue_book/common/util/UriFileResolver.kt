package com.example.blue_book.common.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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

	fun copyToCacheFile(uri: Uri, filenamePrefix: String): File? {
		return try {
			val mime = mimeTypeOf(uri)
			val suffix = when (mime?.lowercase(Locale.ROOT)) {
				"image/jpeg" -> ".jpg"
				"image/jpg" -> ".jpg"
				"image/png" -> ".png"
				"image/webp" -> ".webp"
				"image/gif" -> ".gif"
				else -> ".tmp"
			}
			val tempFile = File.createTempFile(filenamePrefix, suffix, context.cacheDir)
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
