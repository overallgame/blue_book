package com.example.blue_book.ui.publish

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.blue_book.data.remote.video.PublishRemoteDataSource
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 发布视频：选视频 → 分块上传（2MB/块，秒传断点续传由服务端 uploadedChunks 决定）
 * → complete 合并得到 filePath → publish 提交元数据
 */
@HiltViewModel
class PublishViewModel @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val publishRemote: PublishRemoteDataSource
) : UdfViewModel<PublishIntent, PublishUiState, PublishEffect>(PublishUiState()) {

	companion object {
		private const val CHUNK_SIZE = 2 * 1024 * 1024
	}

	private lateinit var publishFileUri: Uri

	override suspend fun handleIntent(intent: PublishIntent) {
		when (intent) {
			is PublishIntent.SelectMedia -> setState {
				copy(mediaUri = intent.uri, phase = PublishPhase.IDLE, progress = 0)
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

	/** 分块上传视频文件并提交发布，返回是否成功 */
	private suspend fun uploadThenPublish(title: String, description: String): Result<Unit> {
		return withContext(Dispatchers.IO) {
			runCatching {
				// 大小读不到时必须失败：按 -1 会算出 1 块，只上传首块导致视频残缺
				val size = queryFileSize(publishFileUri) ?: error("无法读取视频文件信息，请重新选择")
				val fileName = queryFileName(publishFileUri)
				val md5 = computeMd5(publishFileUri)
				val totalChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)

				val init = publishRemote.initUpload(
					UploadInitRequestDto(fileName = fileName, fileSize = size, fileMd5 = md5, totalChunks = totalChunks)
				).getOrThrow()

				// 秒传命中（skipUpload=true）：服务端已有该文件，跳过逐块上传直接 complete
				val uploaded = if (init.skipUpload) (0 until totalChunks).toSet() else init.uploadedChunks.toSet()
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
				Unit
			}.onFailure { it.printStackTrace() }
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
				publishRemote.uploadChunk(uploadId, index, part).getOrThrow()
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
