package com.example.blue_book.ui.publish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.device.LocationProvider
import com.example.blue_book.data.remote.video.ChunkUploader
import com.example.blue_book.data.remote.video.UploadSourceFactory
import com.example.blue_book.data.remote.video.VideoPublisher
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.provider.IUploadSessionStore
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 发布视频：选视频 → 分块上传（并发 3 片，秒传与断点续传由服务端 uploadedChunks 决定）
 * → complete 合并得到 filePath → publish 提交元数据
 *
 * 上传失败会**续传而非从头再来**：重新 initUpload（带同一指纹）拿到已落盘的分片列表后继续；
 * 而且指纹与"上次传到哪了"都记在本地账本（[IUploadSessionStore]），
 * 所以**进程被杀之后**再进来也能接着传——这是本阶段（③）的主要目的。
 *
 * 文件读取、multipart 拼装、并发分片、重试与账本读写都在 [ChunkUploader]（data 层），
 * 本类只管页面状态与流程编排。
 */
@HiltViewModel
class PublishViewModel @Inject constructor(
	private val publisher: VideoPublisher,
	private val uploader: ChunkUploader,
	private val sessionStore: IUploadSessionStore,
	/** 由 `content://` 构造内容源：平台细节收在实现里，本类因此不认识 Context */
	private val sourceFactory: UploadSourceFactory,
	/** 发布时带上的城市（拿不到就为 null，只影响进不进「本地」流） */
	private val locationProvider: LocationProvider,
	private val savedStateHandle: SavedStateHandle
) : UdfViewModel<PublishIntent, PublishUiState, PublishEffect>(
	// 进程被杀后恢复已选视频，避免用户重新选一遍。
	// 取字符串而不是 Uri：Uri.parse 在单元测试里不可用（not mocked）
	PublishUiState(mediaUri = savedStateHandle.get<String>(KEY_MEDIA_URI))
) {

	/**
	 * 当前上传任务的句柄，只为一件事存在：**让"取消"能立刻生效**。
	 *
	 * 不能靠 `dispatch(Cancel)`：UDF 的 intent 由单一 collector 串行消费，
	 * 上传期间发出的取消会排在上传后面、等它跑完才被处理。所以 [requestCancel] 是直接方法，
	 * 取消的是这个 Job（`VideoViewModel.retryInit()` 也是直接方法）。
	 */
	private var uploadJob: Job? = null

	companion object {
		private const val KEY_MEDIA_URI = "publish_media_uri"
	}

	override suspend fun handleIntent(intent: PublishIntent) {
		when (intent) {
			is PublishIntent.SelectMedia -> {
				savedStateHandle[KEY_MEDIA_URI] = intent.uri
				setState {
					copy(mediaUri = intent.uri, phase = PublishPhase.IDLE, progress = 0, resumable = null)
				}
			}
			is PublishIntent.Submit -> submit(intent.title, intent.description)
			PublishIntent.Init -> refreshResumable()
			PublishIntent.OnContinueResumable -> continueResumable()
			PublishIntent.OnDismissResumable -> dismissResumable()
		}
	}

	/**
	 * 用户点"取消上传"。
	 *
	 * 取消后会：① 停掉上传协程（未发出的分片不再发）② 上传器在收尾时调服务端 `abort`
	 * 释放已落盘的分片（否则要等 24 小时的定时清理）③ 丢掉本地账本（用户说了不要，就别再提示）。
	 */
	fun requestCancel() {
		val job = uploadJob ?: return
		job.cancel()
	}

	// ───────────────────────── 上次未完成的上传 ─────────────────────────

	/**
	 * 查本地账本里有没有没传完的。
	 *
	 * 进度先取**本地分片账本**（立刻可见、不等网络），再用 `listParts` 的服务端真实值纠正一次。
	 * 这个顺序是有意的：用户进页面立刻要看到数字，而不是"转一下圈再告诉你传到 68%"；
	 * 而本地那份可能因为进程在"分片落盘"与"账本落库"之间被杀而略微偏低，
	 * 所以能问就问一次。
	 */
	private suspend fun refreshResumable() {
		val session = sessionStore.latestUnfinishedSession() ?: return setState { copy(resumable = null) }
		val parts = sessionStore.getParts(session.uri)
		val done = parts.count { it.status == UploadPartStatus.DONE }
		val localProgress = if (session.totalChunks <= 0) 0 else done * 100 / session.totalChunks
		// 先拿本地的数字（立刻可见、不依赖网络），再问一次服务端的真实值来纠正它；
		// 问不到（离线/会话已被清理）就保持本地那份——横幅不该因为一次查询失败而消失
		val progress = uploader.serverProgress(session.uri) ?: localProgress
		setState {
			copy(
				resumable = ResumableUpload(
					uri = session.uri,
					fileName = session.fileName,
					progress = progress
				)
			)
		}
	}

	/** 把上次未完成的那条填回表单；真正接着传发生在用户点发布时（见 `PublishUiState.resumable`） */
	private suspend fun continueResumable() {
		val resumable = uiState.value.resumable ?: return
		savedStateHandle[KEY_MEDIA_URI] = resumable.uri
		setState {
			copy(
				mediaUri = resumable.uri,
				phase = PublishPhase.IDLE,
				progress = resumable.progress,
				resumable = null
			)
		}
		sendEffect(PublishEffect.ShowToast("已恢复上次的视频，填好标题后点发布即可接着传"))
	}

	/** 不再提示，并把这条作废（本地删账本 + 服务端 abort，别让分片占着磁盘等过期清理） */
	private suspend fun dismissResumable() {
		val session = sessionStore.latestUnfinishedSession()
		if (session != null) {
			session.uploadId?.let { uploader.abort(it) }
			sessionStore.deleteSession(session.uri)
		}
		setState { copy(resumable = null) }
	}

	// ───────────────────────── 发布 ─────────────────────────

	private suspend fun submit(titleInput: String, descriptionInput: String) {
		val state = uiState.value
		val uri = state.mediaUri ?: return sendEffect(PublishEffect.ShowToast("请先选择视频"))
		val title = titleInput.trim()
		if (title.isEmpty()) return sendEffect(PublishEffect.ShowToast("请输入标题"))
		if (state.isBusy) return

		setState { copy(phase = PublishPhase.UPLOADING, progress = 0) }
		// 单独 launch 一个 Job 而不是用 runResult：需要一个能被取消的句柄。
		// join() 让 handleIntent 在这里等它结束，保持"intent 串行消费"的既有语义
		val job = viewModelScope.launch {
			try {
				val result = uploadThenPublish(title, descriptionInput.trim(), uri)
				setState { copy(phase = PublishPhase.IDLE, progress = if (result.isSuccess) 100 else progress) }
				result.fold(
					onSuccess = {
						sendEffect(PublishEffect.ShowToast("发布成功，转码完成后即可播放"))
						sendEffect(PublishEffect.PublishSuccess)
					},
					onFailure = { e -> sendEffect(PublishEffect.ShowToast(e.message ?: "发布失败")) }
				)
			} catch (cancel: CancellationException) {
				// 用户主动取消：不是失败，所以**不弹错误提示**，只把状态收回来。
				// 取消必须继续透传，否则协程会"正常完成"、父作用域观察不到取消
				// （UdfViewModel.runResult 的注释里写着同一条规矩）
				setState { copy(phase = PublishPhase.IDLE) }
				sendEffect(PublishEffect.ShowToast("已取消上传"))
				throw cancel
			}
		}
		uploadJob = job
		job.join()
		uploadJob = null
	}

	/**
	 * 上传视频文件并提交发布。
	 *
	 * 关键约束：**只重试上传部分，绝不重试 `publish`**。
	 * 上传链条（init / uploadChunk / complete）都是可重入的（同一指纹命中服务端已落盘的分片；
	 * `completeUpload` 对 DONE 会话幂等），由 [ChunkUploader] 内部重试并续传。而 `publish`
	 * 每次调用都会新插一行，一旦把它放进重试循环，响应丢失时重试会再发布一次，
	 * 同一条视频出现两条记录且无人对账——所以它只调用一次、不重试。
	 */
	private suspend fun uploadThenPublish(title: String, description: String, uri: String): Result<Unit> {
		return try {
			// 进度取 maxOf：服务端返回的已传分片不保证是前缀，续传回填可能低于当前显示值，
			// 直接用会让进度条往回跳（观感上像丢了数据）
			// 内容源在 UI 层构造（它需要 Context 与 Uri），上传器只认 UploadSource 抽象——
			// 于是"怎么传"完全可测，"怎么读 content://"被隔离在一个文件里
			val filePath = uploader.upload(sourceFactory.create(uri)) { percent ->
				setState { copy(progress = maxOf(progress, percent)) }
			}

			// 上传完了，接下来是提交元数据：把 phase 推进到 PUBLISHING，让页面能显示"发布中"
			setState { copy(phase = PublishPhase.PUBLISHING) }
			// 发布时定位城市（未授权/失败为 null，"本地"流按此过滤）
			val region = locationProvider.currentCity()
			publisher.publish(
				PublishRequestDto(
					title = title,
					description = description.ifBlank { null },
					filePath = filePath,
					region = region
				)
			).getOrThrow()
			Result.success(Unit)
		} catch (t: Throwable) {
			// 取消必须透传（不能把"用户取消"报成发布失败）。
			// 注意：这里用显式 try/catch 而不是 runCatching —— 后者也捕 Throwable，
			// 会把上面这行重抛重新包成 Result.failure，等于没写。
			if (t is CancellationException) throw t
			Result.failure(t)
		}
	}
}
