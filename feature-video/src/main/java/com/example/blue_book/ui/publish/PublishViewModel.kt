package com.example.blue_book.ui.publish

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.blue_book.data.remote.video.PublishRemoteDataSource
import com.example.blue_book.data.remote.video.VideoUploader
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 发布视频：选视频 → 分块上传（2MB/块，秒传与断点续传由服务端 uploadedChunks 决定）
 * → complete 合并得到 filePath → publish 提交元数据
 *
 * 上传失败会**续传而非从头再来**：重新 initUpload（带同一 fileMd5）拿到已落盘的分片列表后继续。
 *
 * 文件读取、multipart 拼装与分块重试都在 [VideoUploader]（data 层），本类只管页面状态与
 * 流程编排——这样 UI 层不再依赖 okhttp3 类型。
 */
@HiltViewModel
class PublishViewModel @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val publishRemote: PublishRemoteDataSource,
	private val uploader: VideoUploader,
	private val savedStateHandle: SavedStateHandle
) : UdfViewModel<PublishIntent, PublishUiState, PublishEffect>(
	// 进程被杀后恢复已选视频，避免用户重新选一遍
	PublishUiState(mediaUri = savedStateHandle.get<String>(KEY_MEDIA_URI)?.let(Uri::parse))
) {

	companion object {
		private const val KEY_MEDIA_URI = "publish_media_uri"
	}

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

		runResult(
			onStart = { setState { copy(phase = PublishPhase.UPLOADING, progress = 0) } },
			call = { uploadThenPublish(title, descriptionInput.trim(), uri) },
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
	 * 上传视频文件并提交发布。
	 *
	 * 关键约束：**只重试上传部分，绝不重试 `publish`**。
	 * 上传链条（init / uploadChunk / complete）都是可重入的（同一 fileMd5 命中服务端已落盘的
	 * 分片；`completeUpload` 对 DONE 会话幂等），由 [VideoUploader] 内部重试并续传。而 `publish`
	 * 每次调用都会新插一行，一旦把它放进重试循环，响应丢失时重试会再发布一次，同一条视频出现
	 * 两条记录且无人对账——所以它只调用一次、不重试。
	 */
	private suspend fun uploadThenPublish(title: String, description: String, uri: Uri): Result<Unit> {
		return try {
			// 进度取 maxOf：uploadedChunks 不保证是前缀，续传回填可能低于当前显示值，
			// 直接用会让进度条往回跳（观感上像丢了数据）
			val filePath = uploader.upload(uri) { percent ->
				setState { copy(progress = maxOf(progress, percent)) }
			}

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
			Result.success(Unit)
		} catch (t: Throwable) {
			// 取消必须透传（不能把"用户离开页面"报成发布失败）。
			// 注意：这里用显式 try/catch 而不是 runCatching —— 后者也捕 Throwable，
			// 会把上面这行重抛重新包成 Result.failure，等于没写。
			if (t is CancellationException) throw t
			Result.failure(t)
		}
	}
}
