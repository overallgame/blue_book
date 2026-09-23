package com.example.blue_book.ui.publish

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.UploadSessionRecord
import com.example.blue_book.data.UploadSessionStatus
import androidx.lifecycle.SavedStateHandle
import com.example.blue_book.data.device.LocationProvider
import com.example.blue_book.data.remote.video.ChunkUploader
import com.example.blue_book.data.remote.video.UploadSourceFactory
import com.example.blue_book.data.remote.video.InMemoryUploadSessionStore
import com.example.blue_book.data.remote.video.UploadSource
import com.example.blue_book.data.remote.video.VideoPublisher
import com.example.blue_book.data.remote.video.dto2.PublishRequestDto
import com.example.blue_book.data.remote.video.dto2.Video2Dto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 发布页的编排测试（feature-video 的第二个测试源集文件）。
 *
 * 覆盖的是那些**错了不会崩、只会让用户损失一次上传或发出两条视频**的行为：
 * 取消要真的停掉并收掉服务端会话、进度不能倒退、密码般重要的一条——`publish` 只调一次。
 *
 * 这里能这么测，靠的是两个窄接口（[ChunkUploader] / [VideoPublisher]）与一个内存版账本
 * （[InMemoryUploadSessionStore]）：`Context` 仍在构造签名里（给定位用，属另一笔债），
 * 但测试用 Robolectric 之外的简单办法绕开它——见 [MainDispatcherRule] 与下面构造时传的假 Context。
 *
 * 用 `UnconfinedTestDispatcher`：`dispatch` 之后状态已经落地，断言不必配 `advanceUntilIdle`。
 * 命名约定沿用项目既有写法：方法名英文、断言消息中文。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublishViewModelTest {

	@get:Rule
	val mainDispatcherRule = MainDispatcherRule()

	private val videoUri = "content://media/external/video/media/42"
	private val store = InMemoryUploadSessionStore()
	private val uploader = FakeChunkUploader()
	private val publisher = FakeVideoPublisher()

	/**
	 * 构造 ViewModel：**签名里没有任何平台类型**，所以这些假实现就够用了。
	 * 这靠的是前面把两处平台能力（构造内容源、取城市）收进 [UploadSourceFactory] 与
	 * [LocationProvider]——在那之前它要一个 `Context`，而纯 JVM 里给不出来。
	 */
	private fun viewModel() = PublishViewModel(
		publisher = publisher,
		uploader = uploader,
		sessionStore = store,
		sourceFactory = UploadSourceFactory { uri -> FakeUploadSource(uri) },
		locationProvider = object : LocationProvider {
			override suspend fun currentCity(): String? = null
		},
		savedStateHandle = SavedStateHandle()
	)

	// ───────────── 上次未完成的上传（跨进程续传的入口）─────────────

	@Test
	fun `init surfaces an unfinished upload with progress from the local ledger`() = runTest {
		seedUnfinishedSession(doneParts = 3, totalParts = 5, uploadId = "u-old")
		val vm = viewModel()

		vm.dispatch(PublishIntent.Init)

		val resumable = vm.uiState.value.resumable
		assertEquals("必须能查到上次没传完的那条", videoUri, resumable?.uri)
		assertEquals("进度按本地分片账本算（3/5 = 60%），不等网络", 60, resumable?.progress)
		assertEquals("横幅要能显示是哪个文件", "a.mp4", resumable?.fileName)
	}

	@Test
	fun `init prefers the server reported progress over the local ledger`() = runTest {
		// 本地账本说 40%，服务端说 80%（本地的账本可能因为进程在"分片落盘"与"账本落库"之间被杀而偏低）
		seedUnfinishedSession(doneParts = 2, totalParts = 5, uploadId = "u-old")
		uploader.serverProgressValue = 80
		val vm = viewModel()

		vm.dispatch(PublishIntent.Init)

		assertEquals("能问到服务端就用服务端的真实值", 80, vm.uiState.value.resumable?.progress)
	}

	@Test
	fun `init falls back to the local ledger when the server cannot be asked`() = runTest {
		seedUnfinishedSession(doneParts = 2, totalParts = 5, uploadId = "u-old")
		uploader.serverProgressValue = null // 离线或会话已被服务端清理
		val vm = viewModel()

		vm.dispatch(PublishIntent.Init)

		assertEquals(
			"问不到服务端就用手里的数字——横幅不该因为一次查询失败而消失",
			40, vm.uiState.value.resumable?.progress
		)
	}

	@Test
	fun `init shows nothing when there is no unfinished upload`() = runTest {
		val vm = viewModel()

		vm.dispatch(PublishIntent.Init)

		assertNull("没有未完成的任务就不该出现横幅", vm.uiState.value.resumable)
	}

	@Test
	fun `continue puts the file back into the form so the user can publish`() = runTest {
		seedUnfinishedSession(doneParts = 2, totalParts = 5, uploadId = "u-old")
		val vm = viewModel()
		val effects = collectEffects(vm)
		vm.dispatch(PublishIntent.Init)

		vm.dispatch(PublishIntent.OnContinueResumable)

		val state = vm.uiState.value
		assertEquals("文件要填回表单，用户不必再去相册里找一遍", videoUri, state.mediaUri)
		assertEquals("进度要接着显示上次的位置", 40, state.progress)
		assertNull("横幅该收起来", state.resumable)
		assertTrue(
			"要说清「接下来该做什么」——发布需要标题，所以不能自动续传",
			effects.filterIsInstance<PublishEffect.ShowToast>().any { it.message.contains("发布") }
		)
	}

	@Test
	fun `dismiss aborts the server session and clears the local ledger`() = runTest {
		seedUnfinishedSession(doneParts = 1, totalParts = 5, uploadId = "u-old")
		val vm = viewModel()
		vm.dispatch(PublishIntent.Init)

		vm.dispatch(PublishIntent.OnDismissResumable)

		assertEquals("忽略要连带收掉服务端会话，别让分片占盘等 24 小时清理", listOf("u-old"), uploader.aborted)
		assertNull("本地账本也要清，否则下次进来又问一遍", store.getSession(videoUri))
		assertNull(vm.uiState.value.resumable)
	}

	// ───────────── 取消上传 ─────────────

	@Test
	fun `cancel stops the upload and does not report it as a failure`() = runTest {
		// 上传卡在"分片传了一半"，此时用户点取消
		uploader.gate = CompletableDeferred()
		val vm = viewModel()
		val effects = collectEffects(vm)
		vm.dispatch(PublishIntent.SelectMedia(videoUri))
		vm.dispatch(PublishIntent.Submit(title = "标题", description = ""))
		assertEquals("应当进入上传态", PublishPhase.UPLOADING, vm.uiState.value.phase)

		vm.requestCancel()

		assertEquals("取消后要回到可以重新操作的状态", PublishPhase.IDLE, vm.uiState.value.phase)
		assertTrue("取消不能被当成失败：那会让用户以为是发布出了问题", uploader.cancelled)
		assertFalse(
			"不能弹「发布失败」这类错误提示，实际提示：${effects.filterIsInstance<PublishEffect.ShowToast>()}",
			effects.filterIsInstance<PublishEffect.ShowToast>().any { it.message.contains("失败") }
		)
	}

	@Test
	fun `cancel is a no op when nothing is uploading`() = runTest {
		val vm = viewModel()

		vm.requestCancel()

		assertEquals("没有任务时点取消不该有任何副作用", PublishPhase.IDLE, vm.uiState.value.phase)
	}

	// ───────────── 守卫与进度 ─────────────

	@Test
	fun `submit without a video only toasts`() = runTest {
		val vm = viewModel()
		val effects = collectEffects(vm)

		vm.dispatch(PublishIntent.Submit(title = "标题", description = ""))

		assertEquals("没选视频不该进入上传态", PublishPhase.IDLE, vm.uiState.value.phase)
		assertTrue(effects.filterIsInstance<PublishEffect.ShowToast>().any { it.message.contains("选择视频") })
		assertEquals("更不该调用发布", 0, publisher.calls.size)
	}

	@Test
	fun `submit without a title only toasts`() = runTest {
		val vm = viewModel()
		val effects = collectEffects(vm)
		vm.dispatch(PublishIntent.SelectMedia(videoUri))

		vm.dispatch(PublishIntent.Submit(title = "   ", description = ""))

		assertEquals("空标题不该进入上传态", PublishPhase.IDLE, vm.uiState.value.phase)
		assertTrue(effects.filterIsInstance<PublishEffect.ShowToast>().any { it.message.contains("标题") })
		assertEquals(0, publisher.calls.size)
	}

	@Test
	fun `progress never goes backwards even if the uploader reports a lower value`() = runTest {
		// 续传回填可能低于当前显示值（服务端返回的已传分片不保证是前缀）
		uploader.progressReports = listOf(10, 70, 40, 100)

		val vm = viewModel()
		val progress = mutableListOf<Int>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
			vm.uiState.collect { progress += it.progress }
		}
		vm.dispatch(PublishIntent.SelectMedia(videoUri))
		vm.dispatch(PublishIntent.Submit(title = "标题", description = ""))

		assertTrue(
			"进度条不能往回跳（观感上像丢了数据），实际序列：$progress",
			progress == progress.sorted()
		)
		assertEquals(100, progress.last())
	}

	// ───────────── 成功与失败 ─────────────

	@Test
	fun `success uploads then publishes and reports success once`() = runTest {
		val vm = viewModel()
		val effects = collectEffects(vm)
		vm.dispatch(PublishIntent.SelectMedia(videoUri))

		vm.dispatch(PublishIntent.Submit(title = "标题", description = "描述"))

		assertEquals("发布只该调一次", 1, publisher.calls.size)
		assertEquals("标题要透传", "标题", publisher.calls.single().title)
		assertEquals("filePath 用上传返回的", "2026-09-22/u1.mp4", publisher.calls.single().filePath)
		assertEquals("完成后回到空闲态", PublishPhase.IDLE, vm.uiState.value.phase)
		assertTrue(
			"要有成功反馈",
			effects.contains(PublishEffect.PublishSuccess)
		)
	}

	@Test
	fun `publish is never retried when it fails`() = runTest {
		// ★ 这条规则有真实后果：publish 每次调用都会新插一行视频，
		// 响应丢失时重试会变成同一条视频两条记录、且无人对账
		publisher.result = Result.failure(RuntimeException("网络连接失败"))
		val vm = viewModel()
		val effects = collectEffects(vm)
		vm.dispatch(PublishIntent.SelectMedia(videoUri))

		vm.dispatch(PublishIntent.Submit(title = "标题", description = ""))

		assertEquals("失败的 publish 不允许重试", 1, publisher.calls.size)
		assertEquals("失败后要回到可重试的状态", PublishPhase.IDLE, vm.uiState.value.phase)
		assertTrue(
			"失败原因要透出",
			effects.filterIsInstance<PublishEffect.ShowToast>().any { it.message == "网络连接失败" }
		)
	}

	@Test
	fun `upload failure keeps the user on the page without calling publish`() = runTest {
		uploader.result = Result.failure(RuntimeException("分片缺失，请重新上传缺失的分片"))
		val vm = viewModel()
		val effects = collectEffects(vm)
		vm.dispatch(PublishIntent.SelectMedia(videoUri))

		vm.dispatch(PublishIntent.Submit(title = "标题", description = ""))

		assertEquals("上传都没成功，绝不能去发布", 0, publisher.calls.size)
		assertEquals(PublishPhase.IDLE, vm.uiState.value.phase)
		assertTrue(
			"服务端的中文文案要透出",
			effects.filterIsInstance<PublishEffect.ShowToast>().any { it.message.contains("分片缺失") }
		)
	}

	// ───────────────────────── 辅助 ─────────────────────────

	private suspend fun seedUnfinishedSession(doneParts: Int, totalParts: Int, uploadId: String) {
		store.seed(
			UploadSessionRecord(
				uri = videoUri, fileName = "a.mp4", fileSize = 5 * 1024 * 1024,
				fileMd5 = "md5", chunkSize = 1024 * 1024, totalChunks = totalParts,
				uploadId = uploadId, status = UploadSessionStatus.UPLOADING,
				updatedAt = System.currentTimeMillis()
			),
			ledger = (0 until totalParts).map { index ->
				UploadPartRecord(
					uri = videoUri, index = index, offset = index * 1024L * 1024,
					size = 1024L * 1024,
					status = if (index < doneParts) UploadPartStatus.DONE else UploadPartStatus.PENDING
				)
			}
		)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun TestScope.collectEffects(vm: PublishViewModel): MutableList<PublishEffect> {
		val effects = mutableListOf<PublishEffect>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
			vm.uiEffect.collect { effects += it }
		}
		return effects
	}
}

/** 最小假内容源：本测试不关心字节，只关心编排（真的读字节由 ChunkedUploaderTest 覆盖） */
private class FakeUploadSource(uri: String) : UploadSource {
	override val name: String = "a.mp4"
	override val key: String = uri
	override val size: Long = 5L * 1024 * 1024
	override suspend fun digest(): String = "md5"
	override fun openReader(): com.example.blue_book.data.remote.video.ChunkReader =
		object : com.example.blue_book.data.remote.video.ChunkReader {
			override val supportsRandomAccess: Boolean = true
			override fun read(offset: Long, size: Int): ByteArray = ByteArray(0)
			override fun close() = Unit
		}
}

/** 假上传器：可以选择成功、失败、或卡住等取消；记录被 abort 过哪些会话 */
private class FakeChunkUploader : ChunkUploader {

	var result: Result<String> = Result.success("2026-09-22/u1.mp4")
	var progressReports: List<Int> = listOf(50, 100)
	var gate: CompletableDeferred<Unit>? = null
	var cancelled: Boolean = false
	val aborted = mutableListOf<String>()

	override suspend fun upload(source: UploadSource, onProgress: (Int) -> Unit): String {
		progressReports.forEach { onProgress(it) }
		gate?.let {
			try {
				it.await()
			} catch (cancel: kotlinx.coroutines.CancellationException) {
				cancelled = true
				throw cancel
			}
		}
		return result.getOrThrow()
	}

	override suspend fun abort(uploadId: String) {
		aborted += uploadId
	}

	/** 服务端能报出的真实进度；null 表示"问不到"（离线 / 会话已被清理） */
	var serverProgressValue: Int? = null

	override suspend fun serverProgress(uri: String): Int? = serverProgressValue
}

/** 假发布器：记录每次调用的请求体，便于断言"只调一次"与参数透传 */
private class FakeVideoPublisher : VideoPublisher {

	val calls = mutableListOf<PublishRequestDto>()
	var result: Result<Video2Dto> = Result.success(
		Video2Dto(
			videoId = 1, uploaderId = 1, uploaderNickname = "n", uploaderAvatar = "",
			title = "t", description = "", coverUrl = "", videoUrl = "",
			likeCount = 0, collectCount = 0, viewCount = 0, commentCount = 0,
			isLike = false, isCollect = false
		)
	)

	override suspend fun publish(body: PublishRequestDto): Result<Video2Dto> {
		calls += body
		return result
	}
}
