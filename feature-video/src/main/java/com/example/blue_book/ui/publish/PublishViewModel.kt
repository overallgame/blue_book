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
				val size = queryFileSize(publishFileUri)
				val fileName = queryFileName(publishFileUri)
				val md5 = computeMd5(publishFileUri)
				val totalChunks = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)

				val init = publishRemote.initUpload(
					UploadInitRequestDto(fileName = fileName, fileSize = size, fileMd5 = md5, totalChunks = totalChunks)
				).getOrThrow()

				// 秒传命中（skipUpload=true）：服务端已有该文件，跳过逐块上传直接 complete
				val uploaded = if (init.skipUpload) (0 until totalChunks).toSet() else init.uploadedChunks.toSet()
				for (index in 0 until totalChunks) {
					if (index in uploaded) continue
					val bytes = readChunk(publishFileUri, index.toLong())
					val part = okhttp3.MultipartBody.Part.createFormData(
						"file", "$fileName.part$index",
						bytes.toRequestBody("application/octet-stream".toMediaType())
					)
					publishRemote.uploadChunk(init.uploadId, index, part).getOrThrow()
					val percent = ((index + 1) * 100 / totalChunks).coerceIn(0, 100)
					setState { copy(progress = percent) }
				}

				val filePath = publishRemote.completeUpload(init.uploadId).getOrThrow()
				publishRemote.publish(PublishRequestDto(title = title, description = description.ifBlank { null }, filePath = filePath))
					.getOrThrow()
				Unit
			}.onFailure { it.printStackTrace() }
		}
	}

	private fun queryFileSize(uri: Uri): Long =
		appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

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

	private fun readChunk(uri: Uri, index: Long): ByteArray {
		appContext.contentResolver.openInputStream(uri)?.use { input ->
			// skip 返回实际跳过的字节数，ContentProvider 可能不完整跳过，需循环累计
			var skipped = 0L
			val skipTarget = index * CHUNK_SIZE
			while (skipped < skipTarget) {
				val n = input.skip(skipTarget - skipped)
				if (n <= 0) break
				skipped += n
			}
			val buffer = ByteArray(CHUNK_SIZE)
			var offset = 0
			while (offset < CHUNK_SIZE) {
				val read = input.read(buffer, offset, CHUNK_SIZE - offset)
				if (read <= 0) break
				offset += read
			}
			return if (offset == CHUNK_SIZE) buffer else buffer.copyOf(offset)
		}
		error("无法读取视频文件")
	}
}
