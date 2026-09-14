package com.example.blue_book.ui.publish

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import com.example.blue_book.data.remote.video.PublishRemoteDataSource
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 发布视频：选视频 → 分块上传（2MB/块，秒传与断点续传由服务端 uploadedChunks 决定）
 * → complete 合并得到 filePath → publish 提交元数据
 *
 * 上传失败会**续传而非从头再来**：重新 initUpload（带同一 fileMd5）拿到已落盘的分片列表后继续。
 */
@HiltViewModel
class PublishViewModel @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val publishRemote: PublishRemoteDataSource,
	private val savedStateHandle: SavedStateHandle
) : UdfViewModel<PublishIntent, PublishUiState, PublishEffect>(
	// 进程被杀后恢复已选视频，避免用户重新选一遍
	PublishUiState(mediaUri = savedStateHandle.get<String>(KEY_MEDIA_URI)?.let(Uri::parse))
) {

	companion object {
		private const val CHUNK_SIZE = 2 * 1024 * 1024

		/** 单块上传的重试次数（字节已在内存，重试无需回退输入流） */
		private const val MAX_CHUNK_RETRY = 3

		/** 整条链（init → 分块 → complete → publish）的重试次数，每次都会续传 */
		private const val MAX_RESUME = 3

		/** 退避基数，按重试次数线性递增 */
		private const val RETRY_BASE_DELAY_MS = 1000L

		private const val KEY_MEDIA_URI = "publish_media_uri"
	}

	private lateinit var publishFileUri: Uri

	override suspend fun handleIntent(intent: PublishIntent) {
		when (intent) {
			is PublishIntent.SelectMedia -> {
				savedStateHandle[KEY_MEDIA_URI] = intent.uri.toString()
				setState { copy(mediaUri = intent.uri, phase = PublishPhase.IDLE, progress = 0) }
			}
			is PublishIntent.Submit -> submit(intent.title, intent.description)
		}
	}

	private suspend fun submit(titleInput: String, descriptionInput: String) {
		val state = uiState.value
		val uri = state.mediaUri ?: return sendEffect(PublishEffect.ShowToast("请先选择视频"))
		val title = titleInput.trim()
		if (title.isEmpty()) return sendEffect(PublishEffect.ShowToast("请输入标题"))
		if (state.isBusy) return

		publishFileUri = uri
		runResult(
			onStart = { setState { copy(phase = PublishPhase.UPLOADING, progress = 0) } },
			call = { uploadThenPublish(title, descriptionInput.trim()) },
			onSuccess = {
				setState { copy(phase = PublishPhase.IDLE, progress = 100) }
				sendEffect(PublishEffect.ShowToast("发布成功，转码完成后即可播放"))
				sendEffect(PublishEffect.PublishSuccess)
			},
			onFailure = { e ->
				setState { copy(phase = PublishPhase.IDLE) }
				sendEffect(PublishEffect.ShowToast(e.message ?: "发布失败"))
			}
		)
	}

	/**
	 * 分块上传视频文件并提交发布，返回是否成功。
	 *
	 * 失败后**续传**：带同一 fileMd5 重新 initUpload，服务端会返回已落盘的分片
	 * （`uploadedChunks`），`uploadChunks` 按偏移跳过它们继续传——
	 * 此前任何一步失败都会中断整条链并丢弃 uploadId，2GB 视频传到 95% 断网就得从零重来。
	 */
	private suspend fun uploadThenPublish(title: String, description: String): Result<Unit> {
		return withContext(Dispatchers.IO) {
			runCatching {
				// 大小读不到时必须失败：按 -1 会算出 1 块，只上传首块导致视频残缺
				val size = queryFileSize(publishFileUri) ?: error("无法读取视频文件信息，请重新选择")
				val fileName = queryFileName(publishFileUri)
				// MD5 只算一次：它决定服务端的续传/秒传命中，重算一次就要多读一遍整个文件
				val md5 = computeMd5(publishFileUri)
				val totalChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
				val initRequest = UploadInitRequestDto(
					fileName = fileName, fileSize = size, fileMd5 = md5, totalChunks = totalChunks
				)

				var lastError: Throwable? = null
				for (attempt in 0 until MAX_RESUME) {
					try {
						val init = publishRemote.initUpload(initRequest).getOrThrow()
						// 秒传命中（skipUpload=true）：服务端已有完整文件，跳过逐块上传直接 complete
						val uploaded =
							if (init.skipUpload) (0 until totalChunks).toSet() else init.uploadedChunks.toSet()
						// 续传时把进度回填到实际已传比例，避免进度条从 0 重新开始
						if (attempt > 0 && uploaded.isNotEmpty()) {
							setState { copy(progress = (uploaded.size * 100 / totalChunks).coerceIn(0, 100)) }
						}
						uploadChunks(init.uploadId, fileName, totalChunks, uploaded)

						val filePath = publishRemote.completeUpload(init.uploadId).getOrThrow()
						// 发布时定位城市（未授权/失败为 null，"本地"流按此过滤）
						val region = runCatching {
							com.example.blue_book.util.LocationHelper.currentCity(appContext)
						}.getOrNull()
						publishRemote.publish(
							PublishRequestDto(
								title = title,
								description = description.ifBlank { null },
								filePath = filePath,
								region = region
							)
						).getOrThrow()
						return@runCatching Unit
					} catch (t: Throwable) {
						// 取消必须透传，否则会把"用户离开页面"当成可重试的失败
						if (t is CancellationException) throw t
						lastError = t
						if (attempt < MAX_RESUME - 1) delay(RETRY_BASE_DELAY_MS * (attempt + 1))
					}
				}
				throw lastError ?: IllegalStateException("上传失败")
			}
		}
	}

	/**
	 * 单次顺序读取完成上传：只打开一次输入流、偏移单调递增，
	 * 已上传分块按偏移跳过（避免逐块重开流 + 从头 skip 造成的大文件 O(n²) 读取）
	 */
	private suspend fun uploadChunks(
		uploadId: String,
		fileName: String,
		totalChunks: Int,
		uploaded: Set<Int>
	) {
		val input = appContext.contentResolver.openInputStream(publishFileUri)
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
				val part = okhttp3.MultipartBody.Part.createFormData(
					"file", "$fileName.part$index",
					bytes.toRequestBody("application/octet-stream".toMediaType())
				)
				// 当前块的字节已在内存中，且 ByteArray 支撑的 RequestBody 可重复写出，
				// 所以单块失败可直接重试，不需要回退输入流
				var chunkAttempt = 0
				while (true) {
					try {
						publishRemote.uploadChunk(uploadId, index, part).getOrThrow()
						break
					} catch (t: Throwable) {
						if (t is CancellationException) throw t
						chunkAttempt++
						if (chunkAttempt >= MAX_CHUNK_RETRY) throw t
						delay(RETRY_BASE_DELAY_MS * chunkAttempt)
					}
				}
				val percent = ((index + 1) * 100 / totalChunks).coerceIn(0, 100)
				setState { copy(progress = percent) }
			}
		}
	}

	/** 跳过量：skip 返回值可能小于请求量，循环补齐；不支持 skip 时退化为读取丢弃 */
	private fun skipFully(input: java.io.InputStream, target: Long) {
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
	private fun readFully(input: java.io.InputStream, max: Int): ByteArray {
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
