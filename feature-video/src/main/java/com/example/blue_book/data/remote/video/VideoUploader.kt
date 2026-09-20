package com.example.blue_book.data.remote.video

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 视频分块上传器：`init → 分块 → complete`，任一步失败都带同一 fileMd5 重新 init 续传。
 *
 * 单独成一个类而不是留在 `PublishViewModel` 里，原因是这一整段是纯粹的传输与文件处理，
 * 与页面状态无关；而且它是全项目**唯一**需要 `okhttp3.MultipartBody` / `RequestBody` 的地方——
 * 留在一处，换网络库或改分块协议就只动这一个文件，UI 层不必跟着改。
 * （此前 PublishViewModel 直接拼 multipart，UI 层因此依赖了网络库类型。）
 *
 * 无状态：文件 uri 由参数传入，不存字段，故不加 scope 注解。
 */
class VideoUploader @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val remote: PublishRemoteDataSource
) {

	companion object {
		private const val CHUNK_SIZE = 2 * 1024 * 1024

		/** 单块上传的重试次数（字节已在内存，重试无需回退输入流） */
		private const val MAX_CHUNK_RETRY = 3

		/** 整条链（init → 分块 → complete）的重试次数，每次都会续传 */
		private const val MAX_RESUME = 3

		/** 退避基数，按重试次数线性递增 */
		private const val RETRY_BASE_DELAY_MS = 1000L
	}

	/**
	 * 把 [uri] 指向的视频分块上传，返回服务端确认的 filePath。
	 *
	 * [onProgress] 在 IO 线程回调（调用方若写 UI 状态需自行保证线程安全，`MutableStateFlow.update`
	 * 本身是安全的）。进度**可能因续传回填而重复上报**，调用方应自行取 `maxOf` 保持只增不减。
	 *
	 * 失败时抛异常（取消异常会原样透传）；可重入：同一 fileMd5 会命中服务端已落盘的分片。
	 */
	suspend fun upload(uri: Uri, onProgress: (Int) -> Unit): String = withContext(Dispatchers.IO) {
		// 大小读不到时必须失败：按 -1 会算出 1 块，只上传首块导致视频残缺
		val size = queryFileSize(uri) ?: error("无法读取视频文件信息，请重新选择")
		val fileName = queryFileName(uri)
		// MD5 只算一次：它决定服务端的续传/秒传命中，重算一次就要多读一遍整个文件
		val md5 = computeMd5(uri)
		val totalChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
		val initRequest = UploadInitRequestDto(
			fileName = fileName, fileSize = size, fileMd5 = md5, totalChunks = totalChunks
		)
		uploadWithResume(uri, initRequest, fileName, totalChunks, onProgress)
	}

	/**
	 * init → 分块 → complete，失败后退避并带同一 fileMd5 重新 init 续传。
	 * 返回服务端确认的 filePath。
	 */
	private suspend fun uploadWithResume(
		uri: Uri,
		initRequest: UploadInitRequestDto,
		fileName: String,
		totalChunks: Int,
		onProgress: (Int) -> Unit
	): String {
		var lastError: Throwable? = null
		for (attempt in 0 until MAX_RESUME) {
			try {
				val init = remote.initUpload(initRequest).getOrThrow()
				// 秒传命中（skipUpload=true）：服务端已有完整文件，跳过逐块上传直接 complete
				val uploaded =
					if (init.skipUpload) (0 until totalChunks).toSet() else init.uploadedChunks.toSet()
				// 续传时把进度回填到实际已传比例。uploadedChunks 不保证是前缀
				// （如 {0..49, 60..99}），所以调用方必须取 maxOf，否则进度条会往回跳、像丢了数据
				if (attempt > 0 && uploaded.isNotEmpty()) {
					onProgress((uploaded.size * 100 / totalChunks).coerceIn(0, 100))
				}
				uploadChunks(uri, init.uploadId, fileName, totalChunks, uploaded, onProgress)
				return remote.completeUpload(init.uploadId).getOrThrow()
			} catch (t: Throwable) {
				if (t is CancellationException) throw t
				lastError = t
				if (attempt < MAX_RESUME - 1) delay(RETRY_BASE_DELAY_MS * (attempt + 1))
			}
		}
		throw lastError ?: IllegalStateException("上传失败")
	}

	/**
	 * 单次顺序读取完成上传：只打开一次输入流、偏移单调递增，
	 * 已上传分块按偏移跳过（避免逐块重开流 + 从头 skip 造成的大文件 O(n²) 读取）
	 */
	private suspend fun uploadChunks(
		uri: Uri,
		uploadId: String,
		fileName: String,
		totalChunks: Int,
		uploaded: Set<Int>,
		onProgress: (Int) -> Unit
	) {
		val input = appContext.contentResolver.openInputStream(uri)
			?: error("无法读取视频文件")
		input.use {
			for (index in 0 until totalChunks) {
				if (index in uploaded) {
					skipFully(it, CHUNK_SIZE.toLong())
					continue
				}
				val bytes = readFully(it, CHUNK_SIZE)
				if (bytes.isEmpty() && index < totalChunks - 1) {
					error("视频文件读取不完整，请重新选择")
				}
				val part = MultipartBody.Part.createFormData(
					"file", "$fileName.part$index",
					bytes.toRequestBody("application/octet-stream".toMediaType())
				)
				// 当前块的字节已在内存中，且 ByteArray 支撑的 RequestBody 可重复写出，
				// 所以单块失败可直接重试，不需要回退输入流
				var chunkAttempt = 0
				while (true) {
					try {
						remote.uploadChunk(uploadId, index, part).getOrThrow()
						break
					} catch (t: Throwable) {
						if (t is CancellationException) throw t
						chunkAttempt++
						if (chunkAttempt >= MAX_CHUNK_RETRY) throw t
						delay(RETRY_BASE_DELAY_MS * chunkAttempt)
					}
				}
				onProgress(((index + 1) * 100 / totalChunks).coerceIn(0, 100))
			}
		}
	}

	/** 跳过量：skip 返回值可能小于请求量，循环补齐；不支持 skip 时退化为读取丢弃 */
	private fun skipFully(input: InputStream, target: Long) {
		var skipped = 0L
		while (skipped < target) {
			val n = input.skip(target - skipped)
			if (n > 0) {
				skipped += n
				continue
			}
			if (input.read() < 0) return
			skipped++
		}
	}

	/** 读取最多 max 字节（末尾分块允许不足） */
	private fun readFully(input: InputStream, max: Int): ByteArray {
		val buffer = ByteArray(max)
		var offset = 0
		while (offset < max) {
			val read = input.read(buffer, offset, max - offset)
			if (read <= 0) break
			offset += read
		}
		return if (offset == max) buffer else buffer.copyOf(offset)
	}

	/** 视频文件大小；无法解析（UNKNOWN_LENGTH/异常）时返回 null */
	private fun queryFileSize(uri: Uri): Long? {
		val length = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: return null
		return length.takeIf { it > 0 }
	}

	private fun queryFileName(uri: Uri): String {
		appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
			if (cursor.moveToFirst()) {
				val name = cursor.getString(0)
				if (!name.isNullOrBlank()) return name
			}
		}
		return "video_${System.currentTimeMillis()}.mp4"
	}

	private fun computeMd5(uri: Uri): String {
		val digest = MessageDigest.getInstance("MD5")
		appContext.contentResolver.openInputStream(uri)?.use { input ->
			val buffer = ByteArray(64 * 1024)
			while (true) {
				val read = input.read(buffer)
				if (read <= 0) break
				digest.update(buffer, 0, read)
			}
		}
		return digest.digest().joinToString("") { "%02x".format(it) }
	}
}
