package com.example.blue_book.data.remote.video

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.UploadSessionRecord
import com.example.blue_book.data.UploadSessionStatus
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.data.upload.reconcile
import com.example.blue_book.network.exception.isRetryableNetworkFailure
import com.example.blue_book.provider.IUploadSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 分片上传：`init → 对账 → 并发传缺片 → complete`，任一步失败都带同一指纹重新 init 续传。
 *
 * 分片以工作池并发上传（默认 3 片），每 worker **独占一个 reader**、从 FIFO 队列取片：
 * 并发度就是 reader 数，不会被"文件里有多少片"带偏；而源不支持随机读时并发退化为 1，
 * 此时队列的 FIFO 顺序让偏移单调递增，与单流顺序上传等价。
 *
 * 分片大小以服务端下发的为准；只重试网络类与 5xx（4xx/业务码直接上抛给整链续传）；
 * 每次 resume 都与服务端的权威分片列表**对账**（见 [reconcile]）。
 */
class ChunkedUploader internal constructor(
	private val remote: ChunkUploadRemote,
	private val sessionStore: IUploadSessionStore,
	/**
	 * 客户端提议的分片大小。
	 *
	 * 做成构造参数只为了测试能用小数据覆盖多片路径（服务端允许 1MB~8MB，
	 * 测试传下限 1MB，于是"并发/续传/退化"这些路径用 4MB 数据就能走到）。
	 */
	private val proposedChunkSize: Long = DEFAULT_CHUNK_SIZE
) : ChunkUploader {

	@Inject
	constructor(remote: ChunkUploadRemote, sessionStore: IUploadSessionStore) :
		this(remote, sessionStore, DEFAULT_CHUNK_SIZE)

	companion object {
		/** 客户端提议的分片大小。服务端会校验它落在允许区间内 */
		const val DEFAULT_CHUNK_SIZE = 2L * 1024 * 1024

		/**
		 * 分片并发度。
		 *
		 * 不是越高越好：每一片在服务端都是一次落盘，并发过高换不来带宽、
		 * 只会让移动网络上的丢包重传更多。
		 */
		const val MAX_CONCURRENCY = 3

		/** 单片上传的重试次数（字节已在内存，重试无需回退输入流） */
		const val MAX_CHUNK_RETRY = 3

		/** 整条链（init → 分片 → complete）的重试次数，每次都会续传 */
		const val MAX_RESUME = 3

		/** 退避基数，按重试次数线性递增 */
		const val RETRY_BASE_DELAY_MS = 1000L

		/** 分片总数。服务端会按同一个公式校验，对不上直接拒绝 */
		fun chunkCount(size: Long, chunkSize: Long): Int =
			((size + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
	}

	override suspend fun upload(source: UploadSource, onProgress: (Int) -> Unit): String {
		try {
			return withContext(Dispatchers.IO) { runUpload(source, onProgress) }
		} catch (cancel: CancellationException) {
			// 用户取消：把服务端那条会话也收掉，别让分片占着磁盘等 24 小时的定时清理。
			// 同时**丢掉本地账本**——"取消"就是不要了，留着它下次进页面还会提示
			// "有一条没传完（68%）"，与用户的意图相反。
			// 必须在 NonCancellable 里做：协程已被取消，普通挂起调用会立刻再抛一次
			withContext(NonCancellable) {
				abortRememberedSession(source.key)
				sessionStore.deleteSession(source.key)
			}
			throw cancel
		}
	}

	override suspend fun abort(uploadId: String) {
		// 放弃上传是"我已经不要了"：清理失败不必打扰用户
		runCatching { remote.abortUpload(uploadId) }
	}

	override suspend fun serverProgress(uri: String): Int? {
		val session = sessionStore.getSession(uri) ?: return null
		val uploadId = session.uploadId ?: return null
		// 只读查询：不 init、不写账本。失败（含服务端已把会话清掉）返回 null，
		// 调用方用本地账本的数字兜住——"问不到"不该让横幅消失
		val parts = remote.listParts(uploadId).getOrNull() ?: return null
		return percent(parts.uploadedChunks.size, parts.totalChunks)
	}

	private suspend fun runUpload(source: UploadSource, onProgress: (Int) -> Unit): String {
		val key = source.key
		val digest = resolveDigest(source, key)
		var lastError: Throwable? = null

		for (attempt in 0 until MAX_RESUME) {
			try {
				val init = remote.initUpload(
					UploadInitRequestDto(
						fileName = source.name,
						fileSize = source.size,
						fileMd5 = digest,
						totalChunks = chunkCount(source.size, proposedChunkSize),
						chunkSize = proposedChunkSize
					)
				).getOrThrow()

				// 用服务端下发的大小切片，而不是自己提议的那个：
				// 服务端只校验提议值并原样回显（越界直接 400），所以两者总是相等；
				// 仍然读响应值，是为了让"用哪个大小"只有服务端一个出处
				val chunkSize = init.chunkSize
				val totalChunks = chunkCount(source.size, chunkSize)
				val serverChunks = if (init.skipUpload) {
					(0 until totalChunks).toSet()
				} else {
					init.uploadedChunks.toSet()
				}

				val ledger = persistAndReconcile(
					key = key, source = source, digest = digest, chunkSize = chunkSize,
					totalChunks = totalChunks, uploadId = init.uploadId, serverChunks = serverChunks
				)

				// 续传时把进度回填到实际已传比例。服务端的列表不保证是前缀
				// （如 {0..49, 60..99}），所以调用方必须取 maxOf，否则进度条会往回跳、像丢了数据
				val alreadyDone = ledger.count { it.status == UploadPartStatus.DONE }
				if (attempt > 0 && alreadyDone > 0) onProgress(percent(alreadyDone, totalChunks))

				uploadMissingChunks(source, key, init.uploadId, ledger, onProgress)

				val path = remote.completeUpload(init.uploadId).getOrThrow()
				// 账本没用了：清掉，否则下次进发布页还会提示"有一条没传完"
				sessionStore.deleteSession(key)
				return path
			} catch (t: Throwable) {
				if (t is CancellationException) throw t
				lastError = t
				// 不可重试的失败意味着"这次请求本身有问题"（分片越界、会话过期、文件过大…）：
				// 本地这份缓存也可能已过时，丢掉它，让下次从服务端重新对账、必要时重算指纹。
				// 服务端已落盘的分片不受影响——reconcile 会从 init/listParts 重新拿回来
				if (!t.isRetryableNetworkFailure()) sessionStore.deleteSession(key)
				if (attempt < MAX_RESUME - 1) delay(RETRY_BASE_DELAY_MS * (attempt + 1))
			}
		}
		throw lastError ?: IllegalStateException("上传失败")
	}

	/**
	 * 指纹：**命中本地缓存就不读文件**。
	 *
	 * 这是本地账本最实在的一处收益：1GB 视频算一次 MD5 要读几十秒，而它发生在任何上传动作之前——
	 * 也就是"用户点了发布之后先卡几十秒"。缓存让第二次及以后（续传）直接跳过这一步。
	 *
	 * 只在**文件大小一致**时才信缓存：同一个 URI 指向的文件被换过（重新录制/编辑）时大小通常会变，
	 * 变了自己重算即可；万一"同大小不同内容"也发生了，最后一道整体 MD5 校验会以 13004 明确失败，
	 * 而那会把这条缓存也丢掉（见上面的 catch），于是下次重算——不会卡死。
	 */
	private suspend fun resolveDigest(source: UploadSource, key: String): String {
		val cached = sessionStore.getSession(key)
		if (cached != null && cached.fileSize == source.size) return cached.fileMd5
		return source.digest()
	}

	/**
	 * 落库 + 对账：**服务端权威 + 本地账本 → 新的账本**。
	 *
	 * 顺序有讲究：先写会话（否则分片账本没有归属），再把对账结果整批写回。
	 * 对账规则本身在 [reconcile]（纯函数，已单独穷举测试）——这里只负责读写。
	 */
	private suspend fun persistAndReconcile(
		key: String,
		source: UploadSource,
		digest: String,
		chunkSize: Long,
		totalChunks: Int,
		uploadId: String,
		serverChunks: Set<Int>
	): List<UploadPartRecord> {
		sessionStore.upsertSession(
			UploadSessionRecord(
				uri = key, fileName = source.name, fileSize = source.size, fileMd5 = digest,
				chunkSize = chunkSize, totalChunks = totalChunks, uploadId = uploadId,
				status = UploadSessionStatus.UPLOADING, updatedAt = System.currentTimeMillis()
			)
		)
		val reconciled = reconcile(
			local = sessionStore.getParts(key),
			serverChunks = serverChunks,
			chunkSize = chunkSize,
			fileSize = source.size
		).map { it.copy(uri = key) }
		sessionStore.replaceParts(key, reconciled)
		return reconciled
	}

	/** 取消时收尾：用本地记着的 uploadId 去 abort（拿不到就什么都不做） */
	private suspend fun abortRememberedSession(key: String) {
		val uploadId = sessionStore.getSession(key)?.uploadId ?: return
		abort(uploadId)
	}

	/** 并发上传账本里所有非 DONE 的片 */
	private suspend fun uploadMissingChunks(
		source: UploadSource,
		key: String,
		uploadId: String,
		ledger: List<UploadPartRecord>,
		onProgress: (Int) -> Unit
	) {
		val pending = ledger.filter { it.status != UploadPartStatus.DONE }
		if (pending.isEmpty()) {
			onProgress(100)
			return
		}

		// 先开一个 reader 探测能力：不可随机定位就只能单流顺序，此时队列的 FIFO 顺序
		// 恰好让偏移单调递增，等价于老实现
		val first = source.openReader()
		val concurrency = if (first.supportsRandomAccess) minOf(MAX_CONCURRENCY, pending.size) else 1
		val readers = buildList {
			add(first)
			repeat(concurrency - 1) { add(source.openReader()) }
		}
		val done = AtomicInteger(ledger.size - pending.size)

		try {
			coroutineScope {
				val queue = Channel<UploadPartRecord>(Channel.UNLIMITED)
				pending.forEach { queue.trySend(it) }
				queue.close()
				readers.forEach { reader ->
					launch {
						for (part in queue) {
							val bytes = reader.read(part.offset, part.size.toInt())
							if (bytes.isEmpty()) error("视频文件读取不完整，请重新选择")
							uploadOnePart(uploadId, source.name, part, bytes)
							// 每片完成就落库：这就是"分片级进度"——它错了不会崩，
							// 但会决定下次进页面时进度条上的数字是不是真的
							sessionStore.updatePartStatus(key, part.index, UploadPartStatus.DONE, part.retryCount)
							// 进度按**已完成片数**算：并发下按 index+1 算会倒退（片是乱序完成的）
							onProgress(percent(done.incrementAndGet(), ledger.size))
						}
					}
				}
			}
		} finally {
			readers.forEach { runCatching { it.close() } }
		}
	}

	/**
	 * 单片上传 + 重试。
	 *
	 * 字节已在内存，且 ByteArray 支撑的 RequestBody 可重复写出，所以重试无需回退输入流。
	 */
	private suspend fun uploadOnePart(
		uploadId: String,
		fileName: String,
		part: UploadPartRecord,
		bytes: ByteArray
	) {
		val body = MultipartBody.Part.createFormData(
			"file", "$fileName.part${part.index}",
			bytes.toRequestBody("application/octet-stream".toMediaType())
		)
		var attempt = 0
		while (true) {
			try {
				remote.uploadChunk(uploadId, part.index, body).getOrThrow()
				return
			} catch (t: Throwable) {
				if (t is CancellationException) throw t
				attempt++
				// 不可重试的错误立刻上抛给整链续传：原地退避只会把失败推迟三秒，
				// 而整链续传会重新 init、跳过已经落盘的片——那条路才是有进展的
				if (!t.isRetryableNetworkFailure() || attempt >= MAX_CHUNK_RETRY) throw t
				delay(RETRY_BASE_DELAY_MS * attempt)
			}
		}
	}

	private fun percent(done: Int, total: Int): Int =
		if (total <= 0) 100 else (done * 100 / total).coerceIn(0, 100)
}
